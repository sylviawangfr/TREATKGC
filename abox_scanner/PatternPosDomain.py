import pandas as pd

from abox_scanner.ContextResources import PatternScanner, ContextResources
from tqdm import tqdm
# domain


class PatternPosDomain(PatternScanner):
    def __init__(self, context_resources: ContextResources) -> None:
        self.pattern_dict = None
        self._context_resources = context_resources

    def scan_pattern_df_rel(self, triples: pd.DataFrame, log_process=True):
        if len(self.pattern_dict) == 0:
            return
        df = triples
        gp = df.query("correct==True").groupby('rel', group_keys=True, as_index=False)
        for g in tqdm(gp, desc="scanning pattern domain", disable=not log_process):
            rel = g[0]
            r_triples_df = g[1]
            if rel in self.pattern_dict:
                correct = self.pattern_dict[rel]
                for idx, row in r_triples_df.iterrows():
                    h_classes = self._context_resources.entid2classids[row['head']]
                    if not any([h_c in correct for h_c in h_classes]):
                        r_triples_df.loc[idx, 'correct'] = False
            else:
                r_triples_df['correct'] = False
            df.update(r_triples_df.query("correct==False")['correct'].apply(lambda x: False))
        return df


    def pattern_to_int(self, entry: str):
        with open(entry) as f:
            pattern_dict = dict()
            lines = f.readlines()
            for l in lines:
                items = l.strip().split('\t')
                r1_uri = items[0][1:-1]
                if r1_uri not in self._context_resources.op2id:
                    continue
                op = self._context_resources.op2id[r1_uri]
                domain = [self._context_resources.class2id[ii[1:-1]] for ii in items[1][:-1].split('\"') if ii not in ['owl:Nothing'] and ii[1:-1] in self._context_resources.class2id]
                pattern_dict.update({op: domain})
            self.pattern_dict = pattern_dict
