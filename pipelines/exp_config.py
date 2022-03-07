class BLPConfig:
    def getComplex(self):
        conf = {
        'dim': 128,
        'model': 'blp',
        'rel_model': 'complex',
        'loss_fn': 'margin',
        'encoder_name': 'bert-base-cased',
        'regularizer': 1e-3,
        'max_len': 32,
        'num_negatives': 64,
        'lr': 2e-5,
        'use_scheduler': True,
        'batch_size': 64,
        'emb_batch_size': 64,
        'eval_batch_size': 32,
        'max_epochs': 40,
        'checkpoint': None,
        'use_cached_text': False
        }
        return conf

    def getSimple(self):
        conf = {
            'dim': 128,
            'model': 'blp',
            'rel_model': 'simple',
            'loss_fn': 'margin',
            'encoder_name': 'bert-base-cased',
            'regularizer': 0,
            'max_len': 32,
            'num_negatives': 64,
            'lr': 2e-5,
            'use_scheduler': True,
            'batch_size': 64,
            'emb_batch_size': 512,
            'eval_batch_size': 64,
            'max_epochs': 40,
            'checkpoint': None,
            'use_cached_text': False
        }
        return conf

    def getTranse(self):
        conf = {
            'dim': 128,
            'model': 'blp',
            'rel_model': 'transe',
            'loss_fn': 'margin',
            'encoder_name': 'bert-base-cased',
            'regularizer': 0,
            'max_len': 32,
            'num_negatives': 64,
            'lr': 1e-5,
            'use_scheduler': True,
            'batch_size': 64,
            'emb_batch_size': 512,
            'eval_batch_size': 64,
            'max_epochs': 40,
            'checkpoint': None,
            'use_cached_text': False
        }
        return conf

    def get_blp_config(self, rel_model, inductive):
        if rel_model == 'transe':
            tmp_conf = self.getTranse()
        elif rel_model == "complex":
            tmp_conf = self.getComplex()
        elif rel_model == "simple":
            tmp_conf = self.getSimple()
        else:
            print(f"{rel_model} is not supported., please use transe, complex or simple")
            return {}
        tmp_conf.update({'inductive': inductive})
        if not inductive:
            tmp_conf.update({'model': 'transductive', 'regularizer': 1e-2, 'lr': 1e-3})
        return tmp_conf



class DatasetConfig:
    input_dir = ""
    schema_file = ""
    tbox_patterns_dir = ""
    max_epoch = 2

    def setTest(self):
        self.input_dir = "../resources/TEST/"
        self.schema_file = '../resources/NELL/tbox.nt'
        self.tbox_patterns_dir = "../resources/NELL-patterns/"
        self.e_max_epoch = 2
        self.exclude_rels = []

    def setNELL(self):
        self.input_dir = "../resources/NELL/"
        self.schema_file = '../resources/NELL/tbox.nt'
        self.tbox_patterns_dir = "../resources/NELL-patterns/"
        self.e_max_epoch = 500
        self.exclude_rels = []

    def setTREAT(self):
        self.input_dir = "../resources/TREAT/"
        self.schema_file = '../resources/TREAT/tbox.nt'
        self.tbox_patterns_dir = "../resources/TREAT/tbox_patterns/"
        self.inductive = False
        self.e_max_epoch = 500
        self.prefix = "http://treat.net/onto.owl#"
        self.exclude_rels = [self.prefix + "has_parameter",
                             self.prefix + "has_property",
                             self.prefix + "alarm_source",
                             self.prefix + "log",
                             self.prefix + "category",
                             self.prefix + "has_additional_info",
                             self.prefix + "sbi_mapping"]

    def setDBpedia(self):
        self.input_dir = "../resources/DBpedia-politics/"
        self.schema_file = '../resources/DBpediaP/dbpedia_2016-10.owl'
        self.tbox_patterns_dir = "../resources/DBpedia-politics/tbox_patterns/"
        self.e_max_epoch = 500
        self.exclude_rels = []

    def get_config(self, dataset):
        if dataset == "NELL":
            self.setNELL()
        elif dataset == "TREAT":
            self.setTREAT()
        elif dataset == "TEST":
            self.setTest()
        elif dataset == "DBpedia":
            self.setDBpedia()
        else:
            print("unsupported dataset")
        return self