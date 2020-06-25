package org.opengroup.osdu.storage.provider.azure;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(MockitoJUnitRunner.class)
public class QueryRepositoryTest {

    @InjectMocks
    private QueryRepositoryImpl repo = new QueryRepositoryImpl();

    @Mock
    private CosmosDBSchema dbSchema;

    @Before
    public void setUp() {
        initMocks(this);
    }

    private static final String KIND1 = "ztenant:source:type:1.0.0";
    private static final String KIND2 = "atenant:source:type:1.0.0";


    @Test
    public void testGetAllKindsNoRecords() {
        // No records found
        List<SchemaDoc> schemaDocs = new ArrayList<>();
        Mockito.when(dbSchema.findAll()).thenReturn(schemaDocs);
        DatastoreQueryResult datastoreQueryResult = repo.getAllKinds(null, null);
        Assert.assertEquals(datastoreQueryResult.getResults(), schemaDocs);
    }

    @Test
    public void testGetAllKindsOneRecord() {
        List<SchemaDoc> schemaDocs = new ArrayList<>();
        schemaDocs.add(getSchemaDoc(KIND1));
        Mockito.when(dbSchema.findAll()).thenReturn(schemaDocs);
        DatastoreQueryResult datastoreQueryResult = repo.getAllKinds(null, null);
        // Expected one kind
        Assert.assertEquals(datastoreQueryResult.getResults().size(), schemaDocs.size());
    }

    @Test
    public void testGetAllKindsMultipleRecord() {
        List<SchemaDoc> schemaDocs = new ArrayList<>();
        schemaDocs.add(getSchemaDoc(KIND1));
        schemaDocs.add(getSchemaDoc(KIND2));
        Mockito.when(dbSchema.findAll()).thenReturn(schemaDocs);
        DatastoreQueryResult datastoreQueryResult = repo.getAllKinds(null, null);
        // expected 2 kinds and they will be sorted ASC by kind name.
        List<String> results = datastoreQueryResult.getResults();
        Assert.assertEquals(results.size(), schemaDocs.size());
        Assert.assertEquals(results.get(0), KIND2);
        Assert.assertEquals(results.get(1), KIND1);
    }

    private SchemaDoc getSchemaDoc(String kind) {
        SchemaDoc doc = new SchemaDoc();
        doc.setKind(kind);
        SchemaItem item = new SchemaItem();
        item.setKind(kind);
        item.setPath("schemaPath");
        SchemaItem[] schemaItems = new SchemaItem[1];
        schemaItems[0] = item;
        doc.setSchemaItems(schemaItems);
        return doc;
    }
}
