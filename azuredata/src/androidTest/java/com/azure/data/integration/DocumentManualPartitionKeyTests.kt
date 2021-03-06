package com.azure.data.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azure.data.AzureData
import com.azure.data.integration.common.DocumentTestsBase
import com.azure.data.integration.common.PartitionedDoc
import com.azure.data.model.service.Response
import org.awaitility.Awaitility.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

@RunWith(AndroidJUnit4::class)
class DocumentManualPartitionKeyTests : DocumentTestsBase<PartitionedDoc>("DocumentManualPartitionKeyTests", PartitionedDoc::class.java) {

    init {
        partitionKeyPath = "/testKey"
        // must match the value extracted from the document (server validates this)
        partitionKeyValue = "PartitionKeyValue"
    }

    @Test
    fun createDocumentWithManualPartitionKey() {

        createNewDocument()
    }

    @Test
    fun createOrUpdateDocumentWithManualPartitionKey() {

        val doc = createNewDocument()

        //change something
        doc.customNumber = customNumberValue + 1

        var docResponse: Response<PartitionedDoc>? = null

        AzureData.createOrUpdateDocument(doc, partitionKeyValue!!, collectionId, databaseId) {
            docResponse = it
        }

        await().until { docResponse != null }

        assertResourceResponseSuccess(docResponse)
        assertEquals(createdResourceId, docResponse?.resource?.id)

        val updatedDoc = docResponse!!.resource!!

        assertNotNull(updatedDoc.customString)
        assertNotNull(updatedDoc.customNumber)
        assertEquals(customNumberValue + 1, updatedDoc.customNumber)
    }
}