from scripts import run_scripts
import argparse
from exp_config import *
from pipelines.pipeline_runner_series import *
from pipelines.pipeline_runner_parallel import *
import torch

# by Sylvia Wang

def get_block_names(name_in_short: str):
    capital_names = name_in_short.upper().strip().split('_')
    supported = ['M', 'A', 'L']
    if any([x not in supported for x in capital_names]):
        print("Unsupported pipeline, please use pipeline names as a_l_m, m_a_l etc.")
        return []
    else:
        return capital_names


def producers(p_config: PipelineConfig):
    if p_config.parallel:
        pipeline_runner = PipelineRunnerParallel()
    else:
        pipeline_runner = PipelineRunnerSeries()
    block_names = get_block_names(p_config.pipeline)
    if len(block_names) == 0:
        return
    else:
        pipeline_runner.run_pipeline(p_config, block_names)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="experiment settings")
    parser.add_argument('--dataset', type=str, default="TREAT")
    parser.add_argument('--work_dir', type=str, default="../outputs/test/")
    parser.add_argument('--pipeline', type=str, default="l")
    parser.add_argument('--use_gpu', type=bool, default=False)
    parser.add_argument('--loops', type=int, default=1)
    parser.add_argument("--rel_model", type=str, default="transe")
    parser.add_argument("--inductive", type=bool, default=False)
    parser.add_argument("--schema_in_nt", type=str, default='../outputs/test/tbox.nt')
    parser.add_argument("--parallel", type=bool, default=False)
    parser.add_argument("--schema_aware", type=bool, default=False)
    parser.add_argument("--reasoner", type=str, default='Konclude')
    parser.add_argument("--pred_type", type=str, default='False')
    args = parser.parse_args()
    if args.parallel:
        torch.multiprocessing.set_start_method('spawn')
    data_conf = DatasetConfig().get_config(args.dataset)
    data_conf.schema_in_nt = args.schema_in_nt
    blp_conf = BLPConfig().get_blp_config(rel_model=args.rel_model,
                                          inductive=args.inductive,
                                          dataset=args.dataset,
                                          schema_aware=args.schema_aware)
    p_config = PipelineConfig().set_pipeline_config(dataset=args.dataset,
                                                    loops=args.loops,
                                                    work_dir=args.work_dir,
                                                    pred_type=args.pred_type,
                                                    reasoner=args.reasoner,
                                                    parallel=args.parallel,
                                                    pipeline=args.pipeline,
                                                    use_gpu=args.use_gpu)
    p_config.set_blp_config(blp_conf).set_data_config(data_conf)
    producers(p_config)
