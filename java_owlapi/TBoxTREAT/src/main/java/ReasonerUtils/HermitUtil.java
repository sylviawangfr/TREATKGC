package ReasonerUtils;

import eu.trowl.owlapi3.rel.reasoner.dl.RELReasonerFactory;
import org.semanticweb.HermiT.Configuration;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.*;
import org.semanticweb.HermiT.Configuration;
import org.semanticweb.HermiT.Reasoner.ReasonerFactory;
import java.util.ArrayList;
import java.util.List;
import eu.trowl.owlapi3.rel.util.InferredSubClassAxiomMultiGenerator;

public class HermitUtil extends ReasonerBase {
    String output_dir;
    public HermitUtil(String output_dir) {
        this.output_dir = output_dir;
    }

    public OWLReasoner getReasoner(OWLOntology ontology) {
        Configuration configuration = new Configuration();
        configuration.ignoreUnsupportedDatatypes = true;
        ReasonerFactory rf = new ReasonerFactory();
        OWLReasoner reasoner = rf.createReasoner(ontology, configuration);
        return reasoner;
    }

    public List<OWLAxiom> getInconsistentSubset(OWLOntology ontology, OWLOntologyManager man, List<OWLAxiom> toCheckAxioms) {
        List<OWLAxiom> inconsistentTriples = new ArrayList<>();
        for(OWLAxiom ax : toCheckAxioms) {
            man.addAxiom(ontology, ax);
            OWLReasoner reasoner = getReasoner(ontology);
            if (!reasoner.isConsistent()) {
                RemoveAxiom removeAxiom = new RemoveAxiom(ontology, ax);
                man.applyChange(removeAxiom);
                inconsistentTriples.add(ax);
            }
        }
        return inconsistentTriples;
    }

    public OWLOntology classify (OWLOntology ontology, OWLOntologyManager man) {
        OWLReasoner reasoner = getReasoner(ontology);
        boolean consistencyCheck = reasoner.isConsistent();
        OWLDataFactory df = man.getOWLDataFactory();
        if (!consistencyCheck) {
            System.out.println("Inconsistent input Ontology, Please check the OWL File");
        }
        //flatten

        System.out.println("Classification, may take time......");
        reasoner.precomputeInferences(
                InferenceType.CLASS_ASSERTIONS,
                InferenceType.CLASS_HIERARCHY,
                InferenceType.SAME_INDIVIDUAL,
                InferenceType.OBJECT_PROPERTY_ASSERTIONS,
                InferenceType.OBJECT_PROPERTY_HIERARCHY,
                InferenceType.DISJOINT_CLASSES
        );
        List<InferredAxiomGenerator<? extends OWLAxiom>> generators = new ArrayList<>();
        generators.add(new InferredSubClassAxiomMultiGenerator());
        InferredOntologyGenerator iog = new InferredOntologyGenerator(reasoner, generators);
        try {
            OWLOntology infOnt = man.createOntology();
            iog.fillOntology(df, infOnt);
            return infOnt;
        } catch (OWLOntologyCreationException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    public OWLOntology realize(OWLOntology ontology, OWLOntologyManager man) {
        // create Hermit reasoner
        OWLReasoner reasoner = getReasoner(ontology);
        boolean consistencyCheck = reasoner.isConsistent();
        if (!consistencyCheck) {
            System.out.println("Inconsistent input Ontology, Please check the OWL File");
        }
        System.out.println("Materialising type assertions and property assertions, may take time......");
        reasoner.precomputeInferences(
                InferenceType.CLASS_ASSERTIONS,
                InferenceType.CLASS_HIERARCHY,
                InferenceType.OBJECT_PROPERTY_ASSERTIONS,
                InferenceType.OBJECT_PROPERTY_HIERARCHY
        );
        List<InferredAxiomGenerator<? extends OWLAxiom>> generators = new ArrayList<>();
        generators.add(new InferredClassAssertionAxiomGenerator());
        generators.add(new InferredPropertyAssertionGenerator());
        List<InferredIndividualAxiomGenerator<? extends OWLIndividualAxiom>> individualAxioms =
                new ArrayList<>();
        generators.addAll(individualAxioms);

        InferredOntologyGenerator iog = new InferredOntologyGenerator(reasoner, generators);
        OWLDataFactory df = man.getOWLDataFactory();
        try {
            OWLOntology inferredAxiomsOntology = man.createOntology();
            iog.fillOntology(df, inferredAxiomsOntology);
            return inferredAxiomsOntology;
        } catch (OWLOntologyCreationException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }
}
