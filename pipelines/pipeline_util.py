from abox_scanner.abox_utils import init_workdir
from module_utils.rumis_util import *
from module_utils.transE_util import *
from openKE import train_transe
from scripts import run_scripts
from module_utils.materialize_util import *
from module_utils.blp_util import *
from blp.producer import ex
from scripts.run_scripts import clean_blp
from module_utils.anyburl_util import *


def aggregate_scores():
    init_kgs, target_kgs, nc, vc, cc, n = [], [], [], [], [], [0]
    def add_new(init_kgc, extend_kgc, new_count, new_valid_count, new_correct_count):
        n[0] = n[0] + 1
        nc.append(new_count)
        vc.append(new_valid_count)
        cc.append(new_correct_count)
        init_kgs.append(init_kgc)
        target_kgs.append(extend_kgc)
        tf_correctness = 0
        tf_consistency = 0
        ta = 0
        ty = 0
        total_new = 0
        for i in range(n[0]):
            if nc[i] == 0:
                continue
            tf_correctness += (cc[i] / nc[i])
            tf_consistency += (vc[i] / nc[i])
            ta += cc[i]
            ty += vc[i]
            total_new += nc[i]

        f_correctness = tf_correctness / n[0]
        f_coverage = ta / init_kgs[0]
        f_h = 2 * f_correctness * f_coverage / (f_coverage + f_correctness) if (f_coverage + f_correctness) > 0 else 0
        f_consistency = tf_consistency / n[0]
        f_coverage2 = ty / init_kgs[0]
        f_h2 = 2 * f_consistency * f_coverage2 / (f_coverage2 + f_consistency) if (f_coverage2 + f_consistency) > 0 else 0
        result = {"init_kgs": init_kgs,
                  "target_kgs": target_kgs,
                  "new_count": nc,
                  "new_valid_count": vc,
                  "new_correct_count": cc,
                  "f_correctness": f_correctness,
                  "f_coverage": f_coverage,
                  "f_correctness_coverage": f_h,
                  "f_consistency": f_consistency,
                  "f_coverage2": f_coverage2,
                  "f_consistency_coverage": f_h2}
        for key in result:
            print(f"{key}: {result[key]}")
        return result

    return add_new


def prepare_context(work_dir, input_dir, schema_file, tbox_patterns_dir="", consistency_check=True,
                    create_id_file=False, abox_file_hrt=""):
    init_workdir(work_dir)
    # prepare tbox patterns
    if tbox_patterns_dir == "" or not os.path.exists(tbox_patterns_dir):
        run_scripts.run_tbox_scanner(schema_file, work_dir)
        tbox_patterns_dir = work_dir + "tbox_patterns/"
    # mv data to work_dir
    os.system(f"cp {input_dir}* {work_dir}")
    # initialize context resource and check consistency
    if abox_file_hrt != "":
        abox_file_path = abox_file_hrt
    else:
        abox_file_path = input_dir + "abox_hrt_uri.txt"
    context_resource = ContextResources(abox_file_path, class_and_op_file_path=work_dir,
                                        work_dir=work_dir, create_id_file=create_id_file)
    # pattern_input_dir, class2int, node2class_int, all_triples_int
    abox_scanner_scheduler = AboxScannerScheduler(tbox_patterns_dir, context_resource)
    # first round scan, get ready for training
    if consistency_check:
        valids, invalids = abox_scanner_scheduler.register_pattern([1, 2, 3, 4, 5, 8, 9, 10, 11, 12, 13],
                                                ['pos_domain', 'pos_range']).scan_IJ_patterns(work_dir=work_dir)
        abox_scanner_scheduler.scan_schema_correct_patterns(work_dir=work_dir)
        wait_until_file_is_saved(work_dir + "valid_hrt.txt")
        context_resource.hrt_int_df = valids
    else:
        abox_scanner_scheduler.register_pattern([1, 2, 3, 4, 5, 8, 9, 10, 11, 12, 13])
        context_resource.hrt_int_df = context_resource.hrt_to_scan_df
    return context_resource, abox_scanner_scheduler


def EC_block(context_resource: ContextResources, abox_scanner_scheduler: AboxScannerScheduler, work_dir, epoch=50,
             use_gpu=False, exclude_rels=[]):
    context_2_hrt_transE(work_dir, context_resource, exclude_rels=exclude_rels)
    wait_until_train_pred_data_ready(work_dir)

    # 1.train transE
    train_transe.train(work_dir + "train/", epoch=epoch, use_gpu=use_gpu)
    wait_until_file_is_saved(work_dir + "checkpoint/transe.ckpt")

    # 2. produce triples
    train_transe.produce(work_dir + "train/", work_dir + "transE_raw_hrts.txt", use_gpu=use_gpu)
    wait_until_file_is_saved(work_dir + "transE_raw_hrts.txt", 30)

    # 3. consistency checking for new triples + old triples
    pred_hrt_df = read_hrts_2_hrt_df(work_dir + "transE_raw_hrts.txt").drop_duplicates(
        keep='first').reset_index(drop=True)

    # diff
    new_hrt_df = pd.concat([pred_hrt_df, context_resource.hrt_int_df, context_resource.hrt_int_df]).drop_duplicates(
        keep=False)
    new_count = len(new_hrt_df.index)
    # scan
    to_scann_hrt_df = pd.concat([context_resource.hrt_int_df, pred_hrt_df], axis=0).drop_duplicates(
        keep='first').reset_index(drop=True)
    # clean
    run_scripts.clean_tranE(work_dir)
    valids, invalids = abox_scanner_scheduler.set_triples_to_scan_int_df(to_scann_hrt_df).scan_IJ_patterns(
        work_dir=work_dir)
    new_valids = pd.concat([valids, context_resource.hrt_int_df, context_resource.hrt_int_df]).drop_duplicates(
        keep=False)
    new_valid_count = len(new_valids.index)
    corrects = abox_scanner_scheduler.scan_schema_correct_patterns(work_dir=work_dir)
    # count
    new_corrects = pd.concat([corrects, context_resource.hrt_int_df, context_resource.hrt_int_df]).drop_duplicates(
        keep=False)
    new_correct_count = len(new_corrects.index)
    del new_corrects
    train_count = len(context_resource.hrt_int_df.index)

    # 5. add new valid hrt to train data
    extend_hrt_df = pd.concat([context_resource.hrt_int_df, valids], axis=0).drop_duplicates(keep='first').reset_index(
        drop=True)
    extend_count = len(extend_hrt_df.index)
    print("update context data")
    context_resource.hrt_int_df = extend_hrt_df
    return train_count, extend_count, new_count, new_valid_count, new_correct_count


def Rumis_C_block(context_resource: ContextResources, abox_scanner_scheduler: AboxScannerScheduler, work_dir):
    # context int to rumis train
    hrt_int_df_2_hrt_rumis(context_resource, work_dir + "ideal.data.txt")
    wait_until_file_is_saved(work_dir + "ideal.data.txt", 120)

    print("running rumis...")
    run_scripts.run_rumis(work_dir)
    check_result = wait_until_file_is_saved(work_dir + "DLV/extension.opm.kg.pos.10.needcheck", 60) \
                   and wait_until_file_is_saved(work_dir + "DLV/extension.opm.kg.neg.10.needcheck", 60)
    if not check_result:
        print({"no result from rumis producer, check logs"})
        run_scripts.clean_rumis(work_dir=work_dir)
        return -1
    else:
        print("rumis one round done")

    # consistency checking for new triples
    new_hrt_df1 = read_hrt_rumis_2_hrt_int_df(work_dir + "DLV/extension.opm.kg.pos.10.needcheck", context_resource)
    new_hrt_df2 = read_hrt_rumis_2_hrt_int_df(work_dir + "DLV/extension.opm.kg.neg.10.needcheck", context_resource)
    pred_hrt_df = pd.concat([context_resource.hrt_int_df, new_hrt_df1, new_hrt_df2], 0).drop_duplicates(
        keep='first').reset_index(drop=True)

    # diff
    new_hrt_df = pd.concat([pred_hrt_df, context_resource.hrt_int_df, context_resource.hrt_int_df]).drop_duplicates(
        keep=False)
    new_count = len(new_hrt_df.index)
    del new_hrt_df

    #  backup and clean last round data
    run_scripts.clean_rumis(work_dir=work_dir)
    valids, invalids = abox_scanner_scheduler.set_triples_to_scan_int_df(pred_hrt_df).scan_IJ_patterns(
        work_dir=work_dir)
    new_valids = pd.concat([valids, context_resource.hrt_int_df, context_resource.hrt_int_df]).drop_duplicates(
        keep=False)
    new_valid_count = len(new_valids.index)
    corrects = abox_scanner_scheduler.scan_schema_correct_patterns(work_dir=work_dir)
    new_corrects = pd.concat([corrects, context_resource.hrt_int_df, context_resource.hrt_int_df]).drop_duplicates(
        keep=False)
    new_correct_count = len(new_corrects.index)
    del new_corrects
    train_count = len(context_resource.hrt_int_df.index)

    # add new valid hrt to train set
    extend_hrt_df = pd.concat([context_resource.hrt_int_df, valids], axis=0).drop_duplicates(keep='first').reset_index(
        drop=True)
    extend_count = len(extend_hrt_df.index)
    # overwrite train data in context
    print("update context data")
    context_resource.hrt_int_df = extend_hrt_df
    return train_count, extend_count, new_count, new_valid_count, new_correct_count


def M_block(context_resource: ContextResources, abox_scanner_scheduler: AboxScannerScheduler, work_dir, schema_in_nt=''):
    # context int to materialization ntriples,
    train_count = len(context_resource.hrt_int_df.index)
    context_resource.to_ntriples(work_dir, schema_in_nt=schema_in_nt)
    wait_until_file_is_saved(work_dir + "tbox_abox.nt", 120)
    # the result is materialized_abox.nt
    print("running materialization...")
    new_ent2types, new_property_assertions = materialize(work_dir,context_resource, abox_scanner_scheduler)
    # merge new types to ent2classes
    new_type_count = update_ent2class(context_resource, new_ent2types)
    # merge new type assertions
    to_scan_df = pd.concat([context_resource.hrt_int_df, new_property_assertions]).drop_duplicates(
        keep='first').reset_index(drop=True)
    valids, _ = abox_scanner_scheduler.set_triples_to_scan_int_df(to_scan_df).scan_IJ_patterns(work_dir)
    new_valids = pd.concat([valids, context_resource.hrt_int_df, context_resource.hrt_int_df]).drop_duplicates(
        keep=False).reset_index(drop=True)
    extend_count = len(valids.index)
    new_count = len(new_valids.index) + new_type_count
    context_resource.hrt_int_df = valids.reset_index(drop=True)
    #  backup and clean last round data
    run_scripts.clean_materialization(work_dir=work_dir)
    print("new type assertions: " + str(new_type_count))
    print("new property assertions: " + str(len(new_valids.index)))
    return train_count, extend_count, new_count, new_count, new_count


def LC_block(context_resource: ContextResources, abox_scanner_scheduler: AboxScannerScheduler,
             work_dir,
             exclude_rels=[], blp_config={}):
    hrt_int_df_2_hrt_blp(context_resource, work_dir,
                         triples_only=False)  # generate all_triples.tsv, entities.txt, relations.txt\
    wait_until_file_is_saved(work_dir + "all_triples.tsv")
    split_all_triples(context_resource, work_dir, inductive=blp_config['inductive'],
                      exclude_rels=exclude_rels)  # split all_triples.tsv to train.tsv, dev.tsv, takes time
    wait_until_blp_data_ready(work_dir, inductive=blp_config['inductive'])
    # 1. run blp
    blp_config.update({'work_dir': work_dir})
    ex.run(config_updates=blp_config)
    wait_until_file_is_saved(work_dir + "blp_new_triples.csv", 60 * 3)

    # 2. consistency checking for new triples
    pred_hrt_df = read_hrts_blp_2_hrt_int_df(work_dir + "blp_new_triples.csv", context_resource).drop_duplicates(
        keep='first').reset_index(drop=True)
    print("all produced triples: " + str(len(pred_hrt_df.index)))
    # diff
    new_hrt_df = pd.concat([pred_hrt_df, context_resource.hrt_int_df,
                            context_resource.hrt_int_df]).drop_duplicates(keep=False)
    new_count = len(new_hrt_df.index)
    print("all old triples: " + str(len(context_resource.hrt_int_df.index)))
    print("all new triples: " + str(new_count))

    # 3. get valid new triples
    clean_blp(work_dir)
    to_scan_df = pd.concat([context_resource.hrt_int_df, new_hrt_df]).drop_duplicates(keep="first").reset_index(
        drop=True)
    valids, invalids = abox_scanner_scheduler.set_triples_to_scan_int_df(to_scan_df).scan_IJ_patterns(work_dir=work_dir)
    new_valids = pd.concat([valids, context_resource.hrt_int_df, context_resource.hrt_int_df]).drop_duplicates(
        keep=False)
    new_valid_count = len(new_valids.index)
    corrects = abox_scanner_scheduler.scan_schema_correct_patterns(work_dir=work_dir).drop_duplicates(keep=False)
    new_corrects = pd.concat([corrects, context_resource.hrt_int_df, context_resource.hrt_int_df]).drop_duplicates(
        keep=False)
    new_correct_count = len(new_corrects.index)
    del new_corrects
    train_count = len(context_resource.hrt_int_df.index)

    # 5. add new valid hrt to train data
    extend_hrt_df = pd.concat([context_resource.hrt_int_df, valids], axis=0).drop_duplicates(keep='first').reset_index(drop=True)
    extend_count = len(extend_hrt_df.index)
    context_resource.hrt_int_df = extend_hrt_df
    return train_count, extend_count, new_count, new_valid_count, new_correct_count


def anyBURL_C_block(context_resource: ContextResources, abox_scanner_scheduler: AboxScannerScheduler, work_dir,
                    exclude_rels=[]):
    mk_dir(work_dir)
    hrt_int_df_2_hrt_anyburl(context_resource, work_dir)
    split_all_triples_anyburl(context_resource, work_dir, exclude_rels=exclude_rels)
    prepare_anyburl_configs(work_dir, pred_with='hr')
    wait_until_anyburl_data_ready(work_dir)
    print("learning anyBURL...")
    run_scripts.learn_anyburl(work_dir)
    print("predicting with anyBURL...")
    run_scripts.predict_with_anyburl(work_dir)
    tmp_pred_hrt1 = read_hrt_pred_anyburl_2_hrt_int_df(work_dir + "predictions/alpha-100",
                                                       context_resource).drop_duplicates(
        keep='first').reset_index(drop=True)
    clean_anyburl_tmp_files(work_dir)
    prepare_anyburl_configs(work_dir, pred_with='rt')
    wait_until_anyburl_data_ready(work_dir)
    print("predicting with anyBURL...")
    run_scripts.predict_with_anyburl(work_dir)
    wait_until_file_is_saved(work_dir + "predictions/alpha-100", 60)
    tmp_pred_hrt2 = read_hrt_pred_anyburl_2_hrt_int_df(work_dir + "predictions/alpha-100",
                                                       context_resource).drop_duplicates(
        keep='first').reset_index(drop=True)
    run_scripts.clean_anyburl(work_dir=work_dir)
    # consistency checking for new triples
    pred_hrt_df = pd.concat([tmp_pred_hrt1, tmp_pred_hrt2]).drop_duplicates(
        keep='first').reset_index(drop=True)
    new_hrt_df = pd.concat([pred_hrt_df, context_resource.hrt_int_df, context_resource.hrt_int_df]).drop_duplicates(
        keep=False)
    new_count = len(new_hrt_df.index)
    #  backup and clean last round data
    run_scripts.clean_anyburl(work_dir=work_dir)
    to_scan_df = pd.concat([context_resource.hrt_int_df, pred_hrt_df]).drop_duplicates(keep="first").reset_index(
        drop=True)
    valids, invalids = abox_scanner_scheduler.set_triples_to_scan_int_df(to_scan_df).scan_IJ_patterns(work_dir=work_dir)
    corrects = abox_scanner_scheduler.scan_schema_correct_patterns(work_dir=work_dir)
    new_valids = pd.concat([valids, context_resource.hrt_int_df, context_resource.hrt_int_df]).drop_duplicates(
        keep=False)
    new_valid_count = len(new_valids.index)
    del new_valids
    new_corrects = pd.concat([corrects, context_resource.hrt_int_df, context_resource.hrt_int_df]).drop_duplicates(
        keep=False)
    new_correct_count = len(new_corrects.index)
    del new_corrects
    train_count = len(context_resource.hrt_int_df.index)

    # add new valid hrt to train set
    extend_hrt_df = pd.concat([context_resource.hrt_int_df, valids], axis=0).drop_duplicates(keep='first').reset_index(drop=True)
    extend_count = len(extend_hrt_df.index)
    # overwrite train data in context
    context_resource.hrt_int_df = extend_hrt_df
    # check rate
    return train_count, extend_count, new_count, new_valid_count, new_correct_count
