package org.opengroup.osdu.storage.provider.azure;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.springframework.data.domain.Sort;

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
    private static final Sort SORT = Sort.by(Sort.Direction.ASC, "kind");


    @Test
    public void testGetAllKindsNoRecords() {
        // No records found
        ArgumentCaptor<Sort> sortArgumentCaptor = ArgumentCaptor.forClass(Sort.class);
        List<SchemaDoc> schemaDocs = new ArrayList<>();
        Mockito.when(dbSchema.findAll(sortArgumentCaptor.capture())).thenReturn(schemaDocs);
        DatastoreQueryResult datastoreQueryResult = repo.getAllKinds(null, null);
        Assert.assertEquals(datastoreQueryResult.getResults(), schemaDocs);
        Assert.assertEquals(sortArgumentCaptor.getValue(), SORT);
    }

    @Test
    public void testGetAllKindsOneRecord() {
        ArgumentCaptor<Sort> sortArgumentCaptor = ArgumentCaptor.forClass(Sort.class);
        List<SchemaDoc> schemaDocs = new ArrayList<>();
        schemaDocs.add(getSchemaDoc(KIND1));
        Mockito.when(dbSchema.findAll(sortArgumentCaptor.capture())).thenReturn(schemaDocs);
        DatastoreQueryResult datastoreQueryResult = repo.getAllKinds(null, null);
        // Expected one kind
        Assert.assertEquals(datastoreQueryResult.getResults().size(), schemaDocs.size());
        Assert.assertEquals(sortArgumentCaptor.getValue(), SORT);
    }

    @Test
    public void testGetAllKindsMultipleRecord() {
        ArgumentCaptor<Sort> sortArgumentCaptor = ArgumentCaptor.forClass(Sort.class);
        List<SchemaDoc> schemaDocs = new ArrayList<>();
        schemaDocs.add(getSchemaDoc(KIND2));
        schemaDocs.add(getSchemaDoc(KIND1));
        Mockito.when(dbSchema.findAll(sortArgumentCaptor.capture())).thenReturn(schemaDocs);
        DatastoreQueryResult datastoreQueryResult = repo.getAllKinds(null, null);
        // expected 2 kinds and they will be sorted ASC by kind name.
        List<String> results = datastoreQueryResult.getResults();
        Assert.assertEquals(results.size(), schemaDocs.size());
        Assert.assertEquals(results.get(0), KIND2);
        Assert.assertEquals(results.get(1), KIND1);
        Assert.assertEquals(sortArgumentCaptor.getValue(), SORT);
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
