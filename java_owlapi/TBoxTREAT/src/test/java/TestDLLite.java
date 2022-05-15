import ReasonerUtils.HermitUtil;
import ReasonerUtils.TrOWLUtil;
import TBoxScanner.PatternDLLite;
import TBoxScanner.TBoxPatternGenerator;
import org.junit.Assert;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

import org.semanticweb.owlapi.formats.NTriplesDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestDLLite {
    DLLite dlliteCvt= new DLLite("output/");
    private static final String TEST_IRI = "http://test/dllite/";
    OWLOntology ontology = null;
    OWLOntologyManager manager = dlliteCvt.man;
    TrOWLUtil trOWLUtil2 = new TrOWLUtil("./");
    HermitUtil hermitUtil = new HermitUtil("./");
    OWLDataFactory factory = dlliteCvt.dataFactory;
    IRI ontologyIRI = IRI.create(TEST_IRI);
    PrefixManager pm = new DefaultPrefixManager(ontologyIRI.toString());

    @Test
    public void testDisjointness() throws OWLOntologyCreationException {
        ontology = manager.createOntology(ontologyIRI);
        OWLClass clsA = factory.getOWLClass(IRI.create(ontologyIRI + "Woman"));
        OWLClass clsB = factory.getOWLClass(IRI.create(ontologyIRI + "Person"));
        OWLClass clsC = factory.getOWLClass(IRI.create(ontologyIRI + "Work"));
        // A subclassof B
        OWLAxiom axiom1 = factory.getOWLSubClassOfAxiom(clsA, clsB);
        AddAxiom addAxiom1 = new AddAxiom(ontology, axiom1);
        manager.applyChange(addAxiom1);
        // B disjointwith C
        OWLAxiom axiom2 = factory.getOWLDisjointClassesAxiom(clsB, clsC);
        AddAxiom addAxiom2 = new AddAxiom(ontology, axiom2);
        manager.applyChange(addAxiom2);
        Map<String, OWLObject> map = new HashMap<>();
        OWLOntology infOnt1 = dlliteCvt.inferAdditionalClass(trOWLUtil2, ontology, map);
        List<String> expect1 = Arrays.asList("<http://test/dllite/Person>", "<http://test/dllite#neg_Work>");
        List<String> expect2 = Arrays.asList("<http://test/dllite#neg_Person>", "<http://test/dllite#neg_Woman>");
        List<String> expect3 = Arrays.asList("<http://test/dllite/Work>", "<http://test/dllite#neg_Person>");
        List<String> expect4 = Arrays.asList("<http://test/dllite/Woman>", "<http://test/dllite#neg_Work>");
        List<String> expect5 = Arrays.asList("<http://test/dllite/Work>", "<http://test/dllite#neg_Woman>");
        List<List<String>> expects = Arrays.asList(expect1, expect2, expect3, expect4, expect5);
        Set<OWLSubClassOfAxiom> subclassof = infOnt1.getAxioms(AxiomType.SUBCLASS_OF);
        List<List<String>> actual = new ArrayList<>();
        for(OWLSubClassOfAxiom ax : subclassof) {
            List<String> ss = Arrays.asList(ax.getSubClass().toString(), ax.getSuperClass().toString());
            actual.add(ss);
        }
        for (List<String> ss : expects) {
            Assert.assertTrue(actual.stream().anyMatch(l -> l.get(0).equals(ss.get(0)) && l.get(1).equals(ss.get(1))));
        }
    }

    @Test
    public void testInverseOfDomainRange() throws OWLOntologyCreationException {
        ontology = manager.createOntology(ontologyIRI);
        OWLClass clsB = factory.getOWLClass(IRI.create(ontologyIRI + "#Person"));
        OWLClass clsC = factory.getOWLClass(IRI.create(ontologyIRI + "#Work"));
        OWLClass clsA = factory.getOWLClass(IRI.create(ontologyIRI + "#Woman"));
        // A subclassof B
        OWLAxiom axiom1 = factory.getOWLSubClassOfAxiom(clsA, clsB);
        AddAxiom addAxiom1 = new AddAxiom(ontology, axiom1);
        manager.applyChange(addAxiom1);
        // B disjointwith C
        OWLAxiom axiom2 = factory.getOWLDisjointClassesAxiom(clsB, clsC);
        AddAxiom addAxiom2 = new AddAxiom(ontology, axiom2);
        manager.applyChange(addAxiom2);
        // inverseof
        OWLObjectProperty hasParent = factory.getOWLObjectProperty(":hasParent", pm);
        OWLObjectProperty hasChild = factory.getOWLObjectProperty(":hasChild", pm);
        OWLAxiom axiom3 = factory.getOWLInverseObjectPropertiesAxiom(hasChild, hasParent);
        AddAxiom addAxiom3 = new AddAxiom(ontology, axiom3);
        manager.applyChange(addAxiom3);
        // domain and range
        OWLObjectProperty work_for = factory.getOWLObjectProperty(":work_for", pm);
        OWLAxiom axiom5 = factory.getOWLObjectPropertyDomainAxiom(work_for, clsB);
        OWLAxiom axiom6 = factory.getOWLObjectPropertyRangeAxiom(work_for, clsC);
        AddAxiom addAxiom5 = new AddAxiom(ontology, axiom5);
        AddAxiom addAxiom6 = new AddAxiom(ontology, axiom6);
        manager.applyChange(addAxiom5);
        manager.applyChange(addAxiom6);
        OWLAxiom axiom7 = factory.getOWLObjectPropertyRangeAxiom(hasParent, clsB);
        OWLAxiom axiom8 = factory.getOWLObjectPropertyDomainAxiom(hasParent, clsB);
        AddAxiom addAxiom7 = new AddAxiom(ontology, axiom7);
        AddAxiom addAxiom8 = new AddAxiom(ontology, axiom8);
        manager.applyChange(addAxiom7);
        manager.applyChange(addAxiom8);

        Map<String, OWLObject> map = new HashMap<>();
//        OWLOntology infOnt1 = dlliteCvt.inferAdditionalClass(trOWLUtil2, ontology, map);
        OWLOntology infOnt = dlliteCvt.ont2dllite(trOWLUtil2, ontology);
        PatternDLLite pattern = new PatternDLLite();
        pattern.SetOWLAPIContext(infOnt, trOWLUtil2.getReasoner(infOnt), factory, "./");
        pattern.generateOPPattern();
    }

    @Test
    public void testInverseOf() throws OWLOntologyCreationException {
        ontology = manager.createOntology(ontologyIRI);
        // inverseof
        OWLObjectProperty hasMother = factory.getOWLObjectProperty(":hasParent", pm);
        OWLObjectProperty hasChild = factory.getOWLObjectProperty(":hasChild", pm);
        OWLAxiom axiom3 = factory.getOWLInverseObjectPropertiesAxiom(hasChild, hasMother);
//        OWLAxiom axiom4 = factory.getOWLSubObjectPropertyOfAxiom(hasChild, factory.getOWLObjectInverseOf(hasMother));
//        OWLAxiom axiom5 = factory.getOWLSubObjectPropertyOfAxiom(hasMother, factory.getOWLObjectInverseOf(hasChild));
//        AddAxiom addAxiom3 = new AddAxiom(ontology, axiom3);
//        AddAxiom addAxiom4 = new AddAxiom(ontology, axiom4);
//        manager.applyChange(addAxiom3);
//        manager.applyChange(addAxiom4);
        for (OWLSubObjectPropertyOfAxiom ax : ((OWLInverseObjectPropertiesAxiom) axiom3).asSubObjectPropertyOfAxioms()) {
            AddAxiom addAx = new AddAxiom(ontology, ax);
            manager.applyChange(addAx);
            break;
        }
        Map<String, OWLObject> map = new HashMap<>();
        OWLOntology infOnt1 = dlliteCvt.inferAdditionalClass(trOWLUtil2, ontology, map);
//        OWLOntology infOnt2 = dlliteCvt.ont2dllite(trOWLUtil2, ontology);
    }

    @Test
    public void testInverseOfPunning() throws OWLOntologyCreationException {
        ontology = manager.createOntology(ontologyIRI);
        OWLClass clsB = factory.getOWLClass(IRI.create(ontologyIRI + "#Person"));
        OWLClass clsC = factory.getOWLClass(IRI.create(ontologyIRI + "#Work"));
        OWLClass clsA = factory.getOWLClass(IRI.create(ontologyIRI + "#Woman"));
        // A subclassof B
        OWLAxiom axiom1 = factory.getOWLSubClassOfAxiom(clsA, clsB);
        AddAxiom addAxiom1 = new AddAxiom(ontology, axiom1);
        manager.applyChange(addAxiom1);
        // B disjointwith C
        OWLAxiom axiom2 = factory.getOWLDisjointClassesAxiom(clsB, clsC);
        AddAxiom addAxiom2 = new AddAxiom(ontology, axiom2);
        manager.applyChange(addAxiom2);
        // inverseof
        OWLObjectProperty hasParent = factory.getOWLObjectProperty(":hasParent", pm);
        OWLObjectProperty hasChild = factory.getOWLObjectProperty(":hasChild", pm);
        OWLAxiom axiom3 = factory.getOWLInverseObjectPropertiesAxiom(hasChild, hasParent);
        AddAxiom addAxiom3 = new AddAxiom(ontology, axiom3);
        manager.applyChange(addAxiom3);
        // domain and range
        OWLObjectProperty work_for = factory.getOWLObjectProperty(":work_for", pm);
        OWLAxiom axiom5 = factory.getOWLObjectPropertyDomainAxiom(work_for, clsB);
        OWLAxiom axiom6 = factory.getOWLObjectPropertyRangeAxiom(work_for, clsC);
        AddAxiom addAxiom5 = new AddAxiom(ontology, axiom5);
        AddAxiom addAxiom6 = new AddAxiom(ontology, axiom6);
        manager.applyChange(addAxiom5);
        manager.applyChange(addAxiom6);
        OWLAxiom axiom7 = factory.getOWLObjectPropertyRangeAxiom(hasParent, clsB);
        OWLAxiom axiom8 = factory.getOWLObjectPropertyDomainAxiom(hasParent, clsB);
        AddAxiom addAxiom7 = new AddAxiom(ontology, axiom7);
        AddAxiom addAxiom8 = new AddAxiom(ontology, axiom8);
        manager.applyChange(addAxiom7);
        manager.applyChange(addAxiom8);
        //add punning
        OWLClass a = factory.getOWLClass(":work_for", pm);
        OWLClass b = factory.getOWLClass(":hasParent", pm);
        OWLClass c = factory.getOWLClass(":hasParent", pm);
        OWLDeclarationAxiom aa = factory.getOWLDeclarationAxiom(a);
        OWLDeclarationAxiom bb = factory.getOWLDeclarationAxiom(b);
        OWLDeclarationAxiom cc = factory.getOWLDeclarationAxiom(c);
        manager.addAxiom(ontology, aa);
        manager.addAxiom(ontology, bb);
        manager.addAxiom(ontology, cc);
        Map<String, OWLObject> map = new HashMap<>();
        OWLOntology infOnt = dlliteCvt.ont2dllite(trOWLUtil2, ontology);
        PatternDLLite pattern = new PatternDLLite();
        pattern.SetOWLAPIContext(infOnt, trOWLUtil2.getReasoner(infOnt), factory, "output/");
        pattern.generatePattern();
    }

    @Test
    public void testSymetric() throws OWLOntologyCreationException {
        ontology = manager.createOntology(ontologyIRI);
        // symetric
        OWLObjectProperty hasFriend = factory.getOWLObjectProperty(":hasFriend", pm);
        OWLAxiom axiom3 = factory.getOWLSymmetricObjectPropertyAxiom(hasFriend);
        AddAxiom addAxiom3 = new AddAxiom(ontology, axiom3);
        manager.applyChange(addAxiom3);
        Map<String, OWLObject> map = new HashMap<>();
        OWLOntology infOnt1 = dlliteCvt.inferAdditionalClass(trOWLUtil2, ontology, map);

    }

    @Test
    public void testSubPropertyOf() throws OWLOntologyCreationException {
        ontology = manager.createOntology(ontologyIRI);
        OWLObjectProperty hasMother = factory.getOWLObjectProperty(":hasMother", pm);
        // subpropertyof
        OWLObjectProperty hasParent = factory.getOWLObjectProperty(":hasParent", pm);
        OWLAxiom axiom4 = factory.getOWLSubObjectPropertyOfAxiom(hasMother, hasParent);
        AddAxiom addAxiom4 = new AddAxiom(ontology, axiom4);
        manager.applyChange(addAxiom4);
        Map<String, OWLObject> map = new HashMap<>();
        OWLOntology infOnt1 = dlliteCvt.inferAdditionalClass(trOWLUtil2, ontology, map);
        List<String> expect1 = Arrays.asList("<http://test/dllite#some_hasMother>", "<http://test/dllite#some_hasParent>");
        List<String> expect2 = Arrays.asList("<http://test/dllite#some_ivs_hasMother>", "<http://test/dllite#some_ivs_hasParent>");
        List<String> expect3 = Arrays.asList("<http://test/dllite#neg_some_hasParent>", "<http://test/dllite#neg_some_hasMother>");
        List<String> expect4 = Arrays.asList("<http://test/dllite#neg_some_ivs_hasParent>", "<http://test/dllite#neg_some_ivs_hasMother>");
        List<List<String>> expects = Arrays.asList(expect1, expect2, expect3, expect4);
        Set<OWLSubClassOfAxiom> subclassof = infOnt1.getAxioms(AxiomType.SUBCLASS_OF);
        List<List<String>> actual = new ArrayList<>();
        for(OWLSubClassOfAxiom ax : subclassof) {
            List<String> ss = Arrays.asList(ax.getSubClass().toString(), ax.getSuperClass().toString());
            actual.add(ss);
        }
        for (List<String> ss : expects) {
            Assert.assertTrue(actual.stream().anyMatch(l -> l.get(0).equals(ss.get(0)) && l.get(1).equals(ss.get(1))));
        }
    }

    @Test
    public void testNELL() throws Exception {
        List<String> toKeepProperties = new ArrayList<>();
        try (Stream<String> lines2 = Files.lines(Paths.get("output/OP.txt"))) {
            lines2.forEach(toKeepProperties::add);
        }
        ontology = dlliteCvt.loadOnto("../../resources/NELL.ontology.ttl");
        IRI ontologyIRI = IRI.create("http://replicate.nell/test");
        OWLOntology clean_ont = manager.createOntology(ontologyIRI);
        OWLDataFactory factory = manager.getOWLDataFactory();
        List<String> all_ops = new ArrayList<>();
        for (OWLObjectProperty op : ontology.getObjectPropertiesInSignature()) {
            all_ops.add(op.getIRI().toString());
            // the maximum  number  of OP that  TrOWL can handle is 302;
            // any more OPs would lead to direct reasoning only.
            if (toKeepProperties.contains(op.getIRI().toString())) {
                for (OWLAxiom ax: ontology.getAxioms(op)) {
                    if (ax.isOfType(AxiomType.INVERSE_OBJECT_PROPERTIES)) {
                        List<OWLObjectProperty> out_of_range = ax.getObjectPropertiesInSignature().stream().filter(ele -> toKeepProperties.contains(ele.getIRI().toString())).collect(Collectors.toList());
                        if (out_of_range.size() > 0) {
                            continue;
                        }
                    }
                    AddAxiom aa = new AddAxiom(clean_ont, ax);
                    manager.applyChange(aa);
                }
            }
        }
        List<String> all_cls = new ArrayList<>();
        for (OWLClass oc : ontology.getClassesInSignature()) {
            if (all_ops.contains(oc.getIRI().toString())) {
                continue;
            } else {
                for (OWLAxiom dec : ontology.getAxioms(oc)) {
                    AddAxiom aa = new AddAxiom(clean_ont, dec);
                    all_cls.add(oc.getIRI().toString());
                    manager.applyChange(aa);
                }
            }
        }
        OWLOntology infOnt = dlliteCvt.ont2dllite(trOWLUtil2, clean_ont);
        NTriplesDocumentFormat nTriplesFormat = new NTriplesDocumentFormat();
        File inferredOntologyFile = new File("output/nell_resized_tbox.nt");
        try (OutputStream outputStream = new FileOutputStream(inferredOntologyFile)) {
            manager.saveOntology(infOnt, nTriplesFormat, outputStream);
        } catch (OWLOntologyStorageException e) {
            e.printStackTrace();
            throw e;
        }
        TBoxPatternGenerator tboxScanner = new TBoxPatternGenerator(infOnt, trOWLUtil2.getReasoner(infOnt), factory, "output/");
        tboxScanner.GeneratePatterns();
        tboxScanner.getAllClasses();
    }

    @Test
    public void testDBpedia() throws Exception {
        ontology = dlliteCvt.loadOnto("../../resources/DBpedia-politics/tbox.nt");
        OWLOntology infOnt = dlliteCvt.ont2dllite(trOWLUtil2, ontology);
        PatternDLLite pattern = new PatternDLLite();
        pattern.SetOWLAPIContext(infOnt, trOWLUtil2.getReasoner(infOnt), factory, "output/");
        pattern.generateOPPattern();
    }
}
