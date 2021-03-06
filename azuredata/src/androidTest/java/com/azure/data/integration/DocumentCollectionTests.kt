package com.azure.data.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azure.data.*
import com.azure.data.constants.HttpHeaderValue
import com.azure.data.integration.common.ResourceTest
import com.azure.data.model.*
import com.azure.data.model.indexing.*
import com.azure.data.model.partition.PartitionKeyRange
import com.azure.data.model.service.ListResponse
import org.awaitility.Awaitility.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

@RunWith(AndroidJUnit4::class)
class DocumentCollectionTests : ResourceTest<DocumentCollection>("DocumentCollectionTests", true, false) {

    init {
        partitionKeyPath = "/testKey"
    }

    @Test
    fun createCollection() {

        ensureCollection()
    }

    @Test
    fun createCollectionWithMinThroughput() {

        ensureCollection(HttpHeaderValue.minDatabaseThroughput)
    }

    @Test
    fun createCollectionWithMaxThroughput() {

        ensureCollection(HttpHeaderValue.maxDatabaseThroughput)
    }

    @Test
    fun createCollectionFailWithInvalidThroughput() {

        val response = tryCreateCollection(750)

        assertErrorResponse(response)
    }

    @Test
    fun createCollectionFromDatabase() {

        database?.createCollection(collectionId, partitionKeyPath!!) {
            response = it
        }

        await().until { response != null }

        assertResourceResponseSuccess(response)
        assertEquals(collectionId, response?.resource?.id)
    }

    @Test
    fun createCollectionFromDatabaseWithThroughput() {

        database?.createCollection(collectionId, HttpHeaderValue.minDatabaseThroughput, partitionKeyPath!!) {
            response = it
        }

        await().until { response != null }

        assertResourceResponseSuccess(response)
        assertEquals(collectionId, response?.resource?.id)
    }

    @Test
    fun createCollectionWithIndexingPolicy() {

        val policy = IndexingPolicy.create {
            automatic = true
            mode = IndexingMode.Lazy
            includedPaths {
                includedPath {
                    path = "/*"
                    indexes {
                        // create indexes via factory methods
                        index(Index.range(DataType.Number, -1))
                        // or, by specifying each member
                        index {
                            kind = IndexKind.Hash
                            dataType = DataType.String
                            precision = 3
                        }
                        index(Index.spatial(DataType.Point))
                    }
                }
            }
            // omit if no paths should be excluded
            excludedPaths {
                excludedPath {
                    path = "/test/*"
                }
            }
        }

        AzureData.createCollection(collectionId, partitionKeyPath!!, policy, databaseId) {
            response = it
        }

        await().until { response != null }

        assertResourceResponseSuccess(response)
        assertEquals(collectionId, response?.resource?.id)
    }

    @Test
    fun listCollections() {

        ensureCollection()

        AzureData.getCollections(databaseId) {
            resourceListResponse = it
        }

        await().until { resourceListResponse != null }

        assertListResponseSuccess(resourceListResponse)
        assertTrue(resourceListResponse?.resource?.count!! > 0)
    }

    @Test
    fun listCollectionsFromDatabase() {

        ensureCollection()

        database?.getCollections {
            resourceListResponse = it
        }

        await().until { resourceListResponse != null }

        assertListResponseSuccess(resourceListResponse)
        assertTrue(resourceListResponse?.resource?.count!! > 0)
    }

    @Test
    fun getCollection() {

        ensureCollection()

        AzureData.getCollection(collectionId, databaseId) {
            response = it
        }

        await().until { response != null }

        assertResourceResponseSuccess(response)
        assertEquals(collectionId, response?.resource?.id)
    }

    @Test
    fun getCollectionFromDatabase() {

        ensureCollection()

        database?.getCollection(collectionId) {
            response = it
        }

        await().until { response != null }

        assertResourceResponseSuccess(response)
        assertEquals(collectionId, response?.resource?.id)
    }

    @Test
    fun refreshCollection() {

        val coll = ensureCollection()

        coll.refresh {
            response = it
        }

        await().until { response != null }

        assertResourceResponseSuccess(response)
        assertEquals(collectionId, response?.resource?.id)
    }

    //region Deletes

    @Test
    fun deleteCollection() {

        val coll = ensureCollection()

        coll.delete {
            dataResponse = it
        }

        await().until { dataResponse != null }

        assertDataResponseSuccess(dataResponse)
    }

    @Test
    fun deleteCollectionByIds() {

        ensureCollection()

        AzureData.deleteCollection(collectionId, databaseId) {
            dataResponse = it
        }

        await().until { dataResponse != null }

        assertDataResponseSuccess(dataResponse)
    }

    @Test
    fun deleteCollectionFromDatabase() {

        val coll = ensureCollection()

        database?.deleteCollection(coll) {
            dataResponse = it
        }

        await().until { dataResponse != null }

        assertDataResponseSuccess(dataResponse)
    }

    @Test
    fun deleteCollectionFromDatabaseById() {

        ensureCollection()

        database?.deleteCollection(collectionId) {
            dataResponse = it
        }

        await().until { dataResponse != null }

        assertDataResponseSuccess(dataResponse)
    }

    //endregion

    @Test
    fun replaceCollection() {

        val collection = ensureCollection()

        val policy = collection.indexingPolicy!!

        policy.excludedPaths?.add(IndexingPolicy.ExcludedPath("/customString/*"))

        AzureData.replaceCollection(collection, databaseId, policy) {
            response = it
        }

        await().until { response != null }

        assertResourceResponseSuccess(response)
        assertEquals(collectionId, response?.resource?.id)

        val newPolicy = response?.resource?.indexingPolicy!!

        assertEquals(policy.automatic, newPolicy.automatic)
        assertEquals(policy.indexingMode, newPolicy.indexingMode)

        //complicated logic to compare the returned policy follows....
        assertEquals(policy.includedPaths!!.size, newPolicy.includedPaths!!.size)

        for (path in policy.includedPaths!!) {

            for (newPath in newPolicy.includedPaths!!) {

                if (newPath.path != path.path) {

                    if (newPath == newPolicy.includedPaths!!.last()) {
                        throw Exception("Included path not found in indexingPolicy")
                    }
                } else {

                    if (path.indexes != null && newPath.indexes != null) {

                        assertEquals(path.indexes!!.size, newPath.indexes!!.size)

                        for (index in path.indexes as List<Index>) {

                            for (newIndex in newPath.indexes as List<Index>) {

                                if (newIndex.dataType == index.dataType ||
                                        newIndex.kind == index.kind ||
                                        newIndex.precision == index.precision) {
                                    break
                                } else if (newIndex == newPath.indexes!!.last()) {
                                    throw Exception("Index not found in included path")
                                }
                            }
                        }
                    }

                    break
                }
            }
        }

        assertEquals(policy.excludedPaths!!.size, newPolicy.excludedPaths!!.size)

        for (path in policy.excludedPaths!!) {

            for (newPath in newPolicy.excludedPaths!!) {

                if (newPath.path != path.path) {

                    if (newPath == newPolicy.excludedPaths!!.last()) {
                        throw Exception("Excluded path not found in indexingPolicy")
                    }
                }
                else {
                    break
                }
            }
        }
    }

    @Test
    fun testGetCollectionPartitionKeyRanges() {

        ensureCollection()

        var response: ListResponse<PartitionKeyRange>? = null

        AzureData.getCollectionPartitionKeyRanges(collectionId, databaseId) {
            response = it
        }

        await().until { response != null }

        assertListResponseSuccess(response)
        assertTrue(response?.resource?.count!! > 0)
    }
}