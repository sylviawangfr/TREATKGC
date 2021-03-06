import copy

import pandas as pd
from tqdm import tqdm
import file_util
from abox_scanner.AboxScannerScheduler import AboxScannerScheduler
from abox_scanner.ContextResources import ContextResources


def typedict2df(ent2types):
    type_list = []
    for ent in ent2types:
        type_list.extend([[ent, 0, t] for t in ent2types[ent]])
    type_df = pd.DataFrame(data=type_list, columns=['head', 'rel', 'tail'])
    return type_df


def filling_type(in_dir, out_dir):
    triples_path = in_dir + "abox_hrt_uri.txt"  # h, t, r
    tbox_patterns_path = in_dir + "tbox_patterns/"
    context_res = ContextResources(triples_path, class_and_op_file_path=in_dir, work_dir=out_dir)
    abox_scanner_scheduler = AboxScannerScheduler(tbox_patterns_path, context_resources=context_res)
    abox_scanner_scheduler.register_patterns_all()
    backup_hrt = context_res.hrt_to_scan_df
    v, inv = abox_scanner_scheduler.scan_rel_IJPs(out_dir, save_result=False)
    corrs, incorr = abox_scanner_scheduler.scan_schema_correct_patterns(out_dir, save_result=False)
    # v = file_util.read_hrt_2_hrt_int_df(in_dir + 'valid_hrt.txt')
    # inv = file_util.read_hrt_2_hrt_int_df(in_dir + 'invalid_hrt.txt')
    # corrs = file_util.read_hrt_2_hrt_int_df(in_dir + 'correct_hrt.txt')
    # incorr = file_util.read_hrt_2_hrt_int_df(in_dir + 'incorrect_hrt.txt')
    lack_of_type_df = pd.concat([incorr, inv]).drop_duplicates(keep=False)
    # lack_of_type_df = incorr
    to_fill = lack_of_type_df.groupby('rel', group_keys=True, as_index=False)
    r2domain = abox_scanner_scheduler.get_schema_correct_strategy_patterns("PatternPosDomain")
    r2range = abox_scanner_scheduler.get_schema_correct_strategy_patterns("PatternPosRange")
    ent2types = copy.deepcopy(context_res.entid2classids)
    for g in tqdm(to_fill, desc="finding range domain to fix"):
        r = g[0]
        r_triples_df = g[1]
        r_D = r2domain[r] if r in r2domain else []
        r_R = r2range[r] if r in r2range else []
        if len(r_D) > 0:
            r_heads = r_triples_df['head'].drop_duplicates(keep='first')
            for ent in r_heads:
                ent_types = ent2types[ent] if ent in ent2types else []
                if any([et in r_D for et in ent_types]):
                    continue
                if ent in ent2types:
                    ent2types[ent].append(r_D[0])
                else:
                    ent2types.update({ent: [r_D[0]]})
        if len(r_R) > 0:
            r_tails = r_triples_df['tail'].drop_duplicates(keep='first')
            for ent in r_tails:
                ent_types = ent2types[ent] if ent in ent2types else []
                if any([et in r_R for et in ent_types]):
                    continue
                if ent in ent2types:
                    ent2types[ent].append(r_R[0])
                else:
                    ent2types.update({ent: [r_R[0]]})
    # context_res.hrt_to_scan_type_df = typedict2df(ent2types)
    # context_res.hrt_int_df = v
    # vt, ivt = abox_scanner_scheduler.scan_type_IJPs(out_dir, save_result=False)
    for e in context_res.entid2classids:
        new_e = ent2types[e]
        if len(context_res.entid2classids[e]) > len(new_e):
            print(e)


    context_res.hrt_to_scan_df = backup_hrt
    context_res.entid2classids = ent2types
    context_res.hrt_int_df = None
    v2, inv2 = abox_scanner_scheduler.scan_rel_IJPs(out_dir, False)
    c2, inc2 = abox_scanner_scheduler.scan_schema_correct_patterns(out_dir, True)

    write_str = []
    for ent in context_res.entid2classids:
        ent_str = context_res.id2ent[ent]
        ent_types = [context_res.classid2class[t] for t in context_res.entid2classids[ent]]
        to_str = f"{ent_str}\t{';'.join(ent_types)}"
        write_str.append(to_str)
    file_util.save_list_to_file(write_str, out_dir + "entity2type.txt", mode='w')


if __name__ == "__main__":
    filling_type("../resources/TREAT/", "../outputs/fix_TREAT/")
    # filling_type("../resources/NELL/", "../outputs/fix_NELL/")
    # filling_type("../resources/DBpedia-politics/", "../outputs/fix_DBpedia/")




