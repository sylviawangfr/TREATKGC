import java.io.*;
import java.util.*;
//import com.clarkparsia.pellet.owlapiv3.PelletReasonerFactory;
import org.semanticweb.HermiT.Configuration;
import org.semanticweb.HermiT.Reasoner.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.NTriplesDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.io.StringDocumentTarget;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
//import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
//import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.util.*;
import uk.ac.manchester.cs.owl.owlapi.OWLSubClassOfAxiomImpl;


public class DLLite {
    public static void owl2dlliteOrginal(String in_file, String out_file) throws Exception {
        System.out.println("To DL-lite: " + in_file);
        // load ontology from file
        File initialFile = new File(in_file);
        InputStream inputStream = new FileInputStream(initialFile);
        // the stream holding the file content
        if (inputStream == null) {
            throw new IllegalArgumentException("file not found! " + in_file);
        }
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology ont = man.loadOntologyFromOntologyDocument(inputStream);
        String base = "http://org.semanticweb.restrictionexample";
        OWLDataFactory factory = man.getOWLDataFactory();
        // remove annotations
        List<OWLAxiom> toRemoveAxiom = new ArrayList<OWLAxiom>();
        toRemoveAxiom.addAll(ont.getAxioms(AxiomType.ANNOTATION_ASSERTION));
        for (OWLAxiom ax: toRemoveAxiom) {
            RemoveAxiom removeAxiom = new RemoveAxiom(ont, ax);
            man.applyChange(removeAxiom);
        }
        // a map to keep additional class IRI to class expression
        Map<String, OWLClassExpression> map = new HashMap<String, OWLClassExpression>();
        // Now create restrictions to describe the class of individual object properties
        System.out.println("Creating D...");
        for (OWLObjectProperty R : ont.getObjectPropertiesInSignature()) {
            if (!R.isNamed()) {
                continue;
            }
            // expression for R and R-
            OWLClassExpression expD1 = factory.getOWLObjectSomeValuesFrom(R, factory.getOWLThing());
            OWLClassExpression expD2 = factory.getOWLObjectSomeValuesFrom(R.getInverseProperty(), factory.getOWLThing());
            // additianal class for (\some R) and (\some R-)
            String nameR = R.getNamedProperty().toStringID();
            if (nameR.contains("#")) {
                nameR = nameR.substring(nameR.lastIndexOf('#') + 1, nameR.length());
            } else {
                nameR = nameR.substring(nameR.lastIndexOf('/') + 1, nameR.length());
            }
            String classNameD1 = base + "#some_" + nameR;
            String classNameD2 = base + "#some_ivs_" + nameR;
            OWLClass D1 = factory.getOWLClass(IRI.create(classNameD1));
            OWLClass D2 = factory.getOWLClass(IRI.create(classNameD2));
            // map D1 to (\some R) and D2 to (\some R-)
            OWLAxiom def1 = factory.getOWLEquivalentClassesAxiom(D1, expD1);
            OWLAxiom def2 = factory.getOWLEquivalentClassesAxiom(D2, expD2);
            man.addAxiom(ont, def1);
            man.addAxiom(ont, def2);
            map.put(D1.getIRI().toString(), expD1);
            map.put(D2.getIRI().toString(), expD2);
        }
        // now create restriction to describe the named classes
        System.out.println("Creating N...");
        for (OWLClass cls: ont.getClassesInSignature()) {
            if (!cls.isNamed()) {
                continue;
            }
            String clsName = cls.toStringID();
            if (clsName.contains("#")) {
                clsName = clsName.substring(clsName.lastIndexOf('#') + 1, clsName.length());
            } else {
                clsName = clsName.substring(clsName.lastIndexOf('/') + 1, clsName.length());
            }
            String negClsName = base + "#neg_" + clsName;
            OWLClass negCls = factory.getOWLClass(IRI.create(negClsName));
            OWLClassExpression expCompl = cls.getObjectComplementOf();
            OWLAxiom negDef = factory.getOWLEquivalentClassesAxiom(negCls, expCompl);
            man.addAxiom(ont, negDef);
            map.put(negCls.getIRI().toString(), expCompl);
        }
        // Schema + Delta, then inference
        Configuration configuration = new Configuration();
        configuration.ignoreUnsupportedDatatypes = true;
        ReasonerFactory rf = new ReasonerFactory();
        OWLReasoner reasoner = rf.createReasoner(ont, configuration); // It takes time to create Hermit reasoner
        System.out.println("Infer Schema + Delta...");
        reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
        List<InferredAxiomGenerator<? extends OWLAxiom>> gens = new ArrayList<InferredAxiomGenerator<? extends OWLAxiom>>();
        gens.add(new InferredSubClassAxiomGenerator());
        OWLOntology infOnt1 = man.createOntology();
        InferredOntologyGenerator iog = new InferredOntologyGenerator(reasoner, gens);
        iog.fillOntology(man.getOWLDataFactory(), infOnt1);
        // merge ont and infOnt1
        System.out.println("merging infOnt1 to ont...");
        OWLOntologyMerger merger = new OWLOntologyMerger(man);
        IRI mergedOntologyIRI1 = IRI.create("http://www.semanticweb.com/merged1");
        OWLOntology merged = merger.createMergedOntology(man, mergedOntologyIRI1);
        System.out.println("Removing ont and infOnt1...");
        man.removeOntology(ont);
        man.removeOntology(infOnt1);

//        // remove redundants: a /sub b, b /sub c, a /sub c ---> delete a /sub c
//        SubClassOfRedundant redtUtil = new SubClassOfRedundant(merged.getAxioms(AxiomType.SUBCLASS_OF));
//        List<OWLSubClassOfAxiom> toRemove = redtUtil.findRedundants();
//        for (OWLAxiom ax: toRemove) {
//            RemoveAxiom removeAxiom = new RemoveAxiom(merged, ax);
//            man.applyChange(removeAxiom);
//        }
        // replace D, N with expressions
        System.out.println("Replace D and N with expressions...");
        Set<OWLSubClassOfAxiom> subclassof = merged.getAxioms(AxiomType.SUBCLASS_OF);
        for (OWLSubClassOfAxiom s : subclassof) {
            OWLClassExpression sub = s.getSubClass();
            OWLClassExpression sup = s.getSuperClass();
            String subIRI = "";
            String supIRI = "";
            if (sub.isNamed()) {
                subIRI = ((OWLClass) sub).getIRI().toString();
            }
            if (sup.isNamed()) {
                supIRI = ((OWLClass) sup).getIRI().toString();
            }
            if (map.containsKey(subIRI) || map.containsKey(supIRI)) {
                OWLClassExpression recoverSub = map.getOrDefault(subIRI, sub);
                OWLClassExpression recoverSup = map.getOrDefault(supIRI, sup);
                OWLSubClassOfAxiom recoverAX = factory.getOWLSubClassOfAxiom(recoverSub, recoverSup);
                // Add the axiom to our ontology
                AddAxiom tmpaddAx = new AddAxiom(merged, recoverAX);
                man.applyChange(tmpaddAx);
            }
        }
        // remove additional classes
        System.out.println("Removing D and N...");
        OWLEntityRemover remover = new OWLEntityRemover(merged);
        for (OWLClass namedClass: merged.getClassesInSignature()) {
            if (map.containsKey(namedClass.getIRI().toString())) {
                namedClass.accept(remover);
            }
        }
        man.applyChanges(remover.getChanges());
        // new schema = Schema.classification ( recover D and N)
        System.out.println("Infer recovered schema + delta ...");
        OWLReasoner reasoner2 = rf.createReasoner(merged, configuration);
        reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
        List<InferredAxiomGenerator<? extends OWLAxiom>> gens2 =
                new ArrayList<InferredAxiomGenerator<? extends OWLAxiom>>();
        gens2.add(new InferredSubClassAxiomGenerator()); //B1 \in B2 or B1 \in \negB2
        OWLOntology infOnt2 = man.createOntology();
        // Now get the inferred ontology generator to generate some inferred
        // axioms for us (into our fresh ontology). We specify the reasoner that
        // we want to use and the inferred axiom generators that we want to use.
        InferredOntologyGenerator iog2 = new InferredOntologyGenerator(reasoner2, gens2);
        iog2.fillOntology(man.getOWLDataFactory(), infOnt2);
        // merge new inferred atoms
        System.out.println("Merging infOnt2 with last round merged...");
        IRI mergedOntologyIRI2 = IRI.create("http://www.semanticweb.com/merged2");
        merged = merger.createMergedOntology(man, mergedOntologyIRI2);
        // remove unwanted axioms like asymmetric etc.
        System.out.println("Removing additional properties ...");
        toRemoveAxiom = new ArrayList<OWLAxiom>();
        toRemoveAxiom.addAll(merged.getAxioms(AxiomType.ASYMMETRIC_OBJECT_PROPERTY));
        toRemoveAxiom.addAll(merged.getAxioms(AxiomType.SYMMETRIC_OBJECT_PROPERTY));
        toRemoveAxiom.addAll(merged.getAxioms(AxiomType.EQUIVALENT_OBJECT_PROPERTIES));
        toRemoveAxiom.addAll(merged.getAxioms(AxiomType.REFLEXIVE_OBJECT_PROPERTY));
        toRemoveAxiom.addAll(merged.getAxioms(AxiomType.IRREFLEXIVE_OBJECT_PROPERTY));
        toRemoveAxiom.addAll(merged.getAxioms(AxiomType.TRANSITIVE_OBJECT_PROPERTY));
        toRemoveAxiom.addAll(merged.getAxioms(AxiomType.INVERSE_OBJECT_PROPERTIES));
        toRemoveAxiom.addAll(merged.getAxioms(AxiomType.SUB_OBJECT_PROPERTY));
        toRemoveAxiom.addAll(merged.getAxioms(AxiomType.DISJOINT_CLASSES));
        toRemoveAxiom.addAll(merged.getAxioms(AxiomType.EQUIVALENT_CLASSES));

        for (OWLAxiom ax: toRemoveAxiom) {
            RemoveAxiom removeAxiom = new RemoveAxiom(merged, ax);
            man.applyChange(removeAxiom);
        }
        // remove redundants: a /sub b, b /sub c, a /sub c ---> delete a /sub c
//        redtUtil = new SubClassOfRedundant(merged.getAxioms(AxiomType.SUBCLASS_OF));
//        for (OWLAxiom ax: redtUtil.findRedundants()) {
//            RemoveAxiom removeAxiom = new RemoveAxiom(merged, ax);
//            man.applyChange(removeAxiom);
//        }
        System.out.println("Saving new ontology " + out_file);
        File inferredOntologyFile = new File(out_file);
        // Now we create a stream since the ontology manager can then write to that stream.
        try (OutputStream outputStream = new FileOutputStream(inferredOntologyFile)) {
            // We use the nt format as for the input ontology.
//             NTriplesDocumentFormat format = new NTriplesDocumentFormat();
            TurtleDocumentFormat format = new TurtleDocumentFormat();
            man.saveOntology(merged, format, outputStream);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public static void owl2dllite(String in_file, String out_file) throws Exception {
        System.out.println("To DL-lite: " + in_file);
        // load ontology from file
        File initialFile = new File(in_file);
        InputStream inputStream = new FileInputStream(initialFile);
        // the stream holding the file content
        if (inputStream == null) {
            throw new IllegalArgumentException("file not found! " + in_file);
        }
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology ont = man.loadOntologyFromOntologyDocument(inputStream);
        String base = "http://org.semanticweb.restrictionexample";
        OWLDataFactory factory = man.getOWLDataFactory();
//        // remove annotations
        List<OWLAxiom> toRemoveAxiom = new ArrayList<OWLAxiom>();
        toRemoveAxiom.addAll(ont.getAxioms(AxiomType.ANNOTATION_ASSERTION));
        for (OWLAxiom ax: toRemoveAxiom) {
            RemoveAxiom removeAxiom = new RemoveAxiom(ont, ax);
            man.applyChange(removeAxiom);
        }
        // a map to keep additional class IRI to class expression
        Map<String, OWLClassExpression> map = new HashMap<String, OWLClassExpression>();
        System.out.println("Creating N...");
        for (OWLClass cls: ont.getClassesInSignature()) {
            if (!cls.isNamed()) {
                continue;
            }
            String clsName = cls.toStringID();
            if (clsName.contains("#")) {
                clsName = clsName.substring(clsName.lastIndexOf('#') + 1, clsName.length());
            } else {
                clsName = clsName.substring(clsName.lastIndexOf('/') + 1, clsName.length());
            }
            String negClsName = base + "#neg_" + clsName;
            OWLClass negCls = factory.getOWLClass(IRI.create(negClsName));
            OWLClassExpression expCompl = cls.getObjectComplementOf();
            OWLAxiom negDef = factory.getOWLEquivalentClassesAxiom(negCls, expCompl);
            man.addAxiom(ont, negDef);
            map.put(negCls.getIRI().toString(), expCompl);
        }
//        reasoner
        // remove class disjointness
        Configuration configuration = new Configuration();
        configuration.ignoreUnsupportedDatatypes = true;
        ReasonerFactory rf = new ReasonerFactory();
        OWLReasoner reasoner1 = rf.createReasoner(ont, configuration); // It takes time to create Hermit reasoner
        System.out.println("Infer A and neg A");
        reasoner1.precomputeInferences(InferenceType.CLASS_HIERARCHY);
        List<InferredAxiomGenerator<? extends OWLAxiom>> gens = new ArrayList<InferredAxiomGenerator<? extends OWLAxiom>>();
        gens.add(new InferredSubClassAxiomGenerator());
        OWLOntology infOnt1 = man.createOntology();
        InferredOntologyGenerator iog1 = new InferredOntologyGenerator(reasoner1, gens);
        iog1.fillOntology(man.getOWLDataFactory(), infOnt1);
        // merge ont and infOnt1
        System.out.println("merging infOnt1 to ont...");
        OWLOntologyMerger merger = new OWLOntologyMerger(man);
        IRI mergedOntologyIRI1 = IRI.create("http://www.semanticweb.com/merged1");
        OWLOntology merged1 = merger.createMergedOntology(man, mergedOntologyIRI1);

        //remove class disjointness
        System.out.println("Removing DISJOINT_CLASSES axioms ...");
        List<OWLAxiom> toRemoveAxiom1 = new ArrayList<OWLAxiom>();
        toRemoveAxiom.addAll(merged1.getAxioms(AxiomType.DISJOINT_CLASSES));
        for (OWLAxiom ax: toRemoveAxiom) {
            RemoveAxiom removeAxiom = new RemoveAxiom(merged1, ax);
            man.applyChange(removeAxiom);
        }
        //keep the merged1 only
        man.removeOntology(ont);
        man.removeOntology(infOnt1);

        // Now create restrictions to describe the class of individual object properties
        System.out.println("Creating D and neg D...");
        for (OWLObjectProperty R : merged1.getObjectPropertiesInSignature()) {
            if (!R.isNamed()) {
                continue;
            }
            // expression for R and R-
            OWLClassExpression expD1 = factory.getOWLObjectSomeValuesFrom(R, factory.getOWLThing());
            OWLClassExpression expD2 = factory.getOWLObjectSomeValuesFrom(R.getInverseProperty(), factory.getOWLThing());
            // additianal class for (\some R) and (\some R-)
            String nameR = R.getNamedProperty().toStringID();
            if (nameR.contains("#")) {
                nameR = nameR.substring(nameR.lastIndexOf('#') + 1, nameR.length());
            } else {
                nameR = nameR.substring(nameR.lastIndexOf('/') + 1, nameR.length());
            }
            String classNameD1 = base + "#some_" + nameR;
            String classNameD2 = base + "#some_ivs_" + nameR;
            OWLClass D1 = factory.getOWLClass(IRI.create(classNameD1));
            OWLClass D2 = factory.getOWLClass(IRI.create(classNameD2));
            // map D1 to (\some R) and D2 to (\some R-)
            OWLAxiom def1 = factory.getOWLEquivalentClassesAxiom(D1, expD1);
            OWLAxiom def2 = factory.getOWLEquivalentClassesAxiom(D2, expD2);
            man.addAxiom(merged1, def1);
            man.addAxiom(merged1, def2);
            map.put(D1.getIRI().toString(), expD1);
            map.put(D2.getIRI().toString(), expD2);
            // add neg for D1
            String negClsNameD1 = base + "#neg_some_" + nameR;
            OWLClass negClsD1 = factory.getOWLClass(IRI.create(negClsNameD1));
            OWLClassExpression expComplD1 = D1.getObjectComplementOf();
            OWLAxiom negDefD1 = factory.getOWLEquivalentClassesAxiom(negClsD1, expComplD1);
            man.addAxiom(merged1, negDefD1);
            map.put(negClsNameD1, expComplD1);
            // add neg for D2
            String negClsNameD2 = base + "#neg_some_ivs_" + nameR;
            OWLClass negClsD2 = factory.getOWLClass(IRI.create(negClsNameD2));
            OWLClassExpression expComplD2 = D2.getObjectComplementOf();
            OWLAxiom negDefD2 = factory.getOWLEquivalentClassesAxiom(negClsD2, expComplD2);
            man.addAxiom(merged1, negDefD2);
            map.put(negClsNameD2, expComplD2);
        }

        // inference D and neg D
        System.out.println("Infer D and neg D...");
        OWLReasoner reasoner2 = rf.createReasoner(merged1, configuration); // It takes time to create Hermit reasoner
        reasoner2.precomputeInferences(InferenceType.CLASS_HIERARCHY);
        OWLOntology infOnt2 = man.createOntology();
        InferredOntologyGenerator iog2 = new InferredOntologyGenerator(reasoner2, gens);
        iog2.fillOntology(man.getOWLDataFactory(), infOnt2);
        // merged1 and infOnt2
        System.out.println("merging infOnt2 to merged1...");
        OWLOntologyMerger merger2 = new OWLOntologyMerger(man);
        IRI mergedOntologyIRI2 = IRI.create("http://www.semanticweb.com/merged2");
        OWLOntology merged2 = merger2.createMergedOntology(man, mergedOntologyIRI2);
        //keep only the merged2
        man.removeOntology(merged1);
        man.removeOntology(infOnt2);
//        // remove redundants: a /sub b, b /sub c, a /sub c ---> delete a /sub c
//        SubClassOfRedundant redtUtil = new SubClassOfRedundant(merged.getAxioms(AxiomType.SUBCLASS_OF));
//        List<OWLSubClassOfAxiom> toRemove = redtUtil.findRedundants();
//        for (OWLAxiom ax: toRemove) {
//            RemoveAxiom removeAxiom = new RemoveAxiom(merged, ax);
//            man.applyChange(removeAxiom);
//        }
        // replace D, N with expressions
        System.out.println("Replace D and N with expressions...");
        Set<OWLSubClassOfAxiom> subclassof = merged2.getAxioms(AxiomType.SUBCLASS_OF);
        for (OWLSubClassOfAxiom s : subclassof) {
            OWLClassExpression sub = s.getSubClass();
            OWLClassExpression sup = s.getSuperClass();
            String subIRI = "";
            String supIRI = "";
            if (sub.isNamed()) {
                subIRI = ((OWLClass) sub).getIRI().toString();
            }
            if (sup.isNamed()) {
                supIRI = ((OWLClass) sup).getIRI().toString();
            }
            if (map.containsKey(subIRI) || map.containsKey(supIRI)) {
                OWLClassExpression recoverSub = map.getOrDefault(subIRI, sub);
                OWLClassExpression recoverSup = map.getOrDefault(supIRI, sup);
                OWLSubClassOfAxiom recoverAX = factory.getOWLSubClassOfAxiom(recoverSub, recoverSup);
                // Add the axiom to our ontology
                AddAxiom tmpaddAx = new AddAxiom(merged2, recoverAX);
                man.applyChange(tmpaddAx);
            }
        }
        // remove additional classes
        System.out.println("Removing D and N...");
        OWLEntityRemover remover = new OWLEntityRemover(merged2);
        for (OWLClass namedClass: merged2.getClassesInSignature()) {
            if (map.containsKey(namedClass.getIRI().toString())) {
                namedClass.accept(remover);
            }
        }
        man.applyChanges(remover.getChanges());
        // new schema = Schema.classification ( recover D and N)
        System.out.println("Infer recovered schema + delta ...");
        OWLReasoner reasoner3 = rf.createReasoner(merged2, configuration);
        reasoner3.precomputeInferences(InferenceType.CLASS_HIERARCHY); //B1 \in B2 or B1 \in \negB2
        OWLOntology infOnt3 = man.createOntology();
        // Now get the inferred ontology generator to generate some inferred
        // axioms for us (into our fresh ontology). We specify the reasoner that
        // we want to use and the inferred axiom generators that we want to use.
        InferredOntologyGenerator iog3 = new InferredOntologyGenerator(reasoner3, gens);
        iog3.fillOntology(man.getOWLDataFactory(), infOnt3);
        // merge new inferred atoms
        System.out.println("Merging infOnt3 and merged2 to merged3");
        IRI mergedOntologyIRI3 = IRI.create("http://www.semanticweb.com/merged3");
        OWLOntology  merged3 = merger.createMergedOntology(man, mergedOntologyIRI3);
        man.removeOntology(merged2);
        man.removeOntology(infOnt3);
        // remove unwanted axioms like asymmetric etc.
        System.out.println("Removing additional properties ...");
        List<OWLAxiom> toRemoveAxiom3 = new ArrayList<OWLAxiom>();
        toRemoveAxiom3.addAll(merged3.getAxioms(AxiomType.ASYMMETRIC_OBJECT_PROPERTY));
        toRemoveAxiom3.addAll(merged3.getAxioms(AxiomType.SYMMETRIC_OBJECT_PROPERTY));
        toRemoveAxiom3.addAll(merged3.getAxioms(AxiomType.EQUIVALENT_OBJECT_PROPERTIES));
        toRemoveAxiom3.addAll(merged3.getAxioms(AxiomType.REFLEXIVE_OBJECT_PROPERTY));
        toRemoveAxiom3.addAll(merged3.getAxioms(AxiomType.IRREFLEXIVE_OBJECT_PROPERTY));
        toRemoveAxiom3.addAll(merged3.getAxioms(AxiomType.TRANSITIVE_OBJECT_PROPERTY));
        toRemoveAxiom3.addAll(merged3.getAxioms(AxiomType.INVERSE_OBJECT_PROPERTIES));
        toRemoveAxiom3.addAll(merged3.getAxioms(AxiomType.SUB_OBJECT_PROPERTY));
        toRemoveAxiom3.addAll(merged3.getAxioms(AxiomType.EQUIVALENT_CLASSES));
        for (OWLAxiom ax: toRemoveAxiom3) {
            RemoveAxiom removeAxiom = new RemoveAxiom(merged3, ax);
            man.applyChange(removeAxiom);
        }
        // remove redundants: a /sub b, b /sub c, a /sub c ---> delete a /sub c
//        redtUtil = new SubClassOfRedundant(merged.getAxioms(AxiomType.SUBCLASS_OF));
//        for (OWLAxiom ax: redtUtil.findRedundants()) {
//            RemoveAxiom removeAxiom = new RemoveAxiom(merged, ax);
//            man.applyChange(removeAxiom);
//        }
        System.out.println("Saving new ontology " + out_file);
        File inferredOntologyFile = new File(out_file);
        // Now we create a stream since the ontology manager can then write to that stream.
        try (OutputStream outputStream = new FileOutputStream(inferredOntologyFile)) {
            // We use the nt format as for the input ontology.
             NTriplesDocumentFormat format = new NTriplesDocumentFormat();
//            TurtleDocumentFormat format = new TurtleDocumentFormat();
            man.saveOntology(merged3, format, outputStream);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
