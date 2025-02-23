package com.wa2c.android.cifsdocumentsprovider.presentation.provider

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.*
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import com.wa2c.android.cifsdocumentsprovider.AppModule
import com.wa2c.android.cifsdocumentsprovider.R
import com.wa2c.android.cifsdocumentsprovider.common.utils.logE
import com.wa2c.android.cifsdocumentsprovider.common.utils.mimeType
import com.wa2c.android.cifsdocumentsprovider.common.utils.pathFragment
import com.wa2c.android.cifsdocumentsprovider.common.values.AccessMode
import com.wa2c.android.cifsdocumentsprovider.common.values.URI_AUTHORITY
import com.wa2c.android.cifsdocumentsprovider.data.CifsClient
import com.wa2c.android.cifsdocumentsprovider.data.preference.AppPreferences
import com.wa2c.android.cifsdocumentsprovider.domain.model.CifsConnection
import com.wa2c.android.cifsdocumentsprovider.domain.model.CifsFile
import com.wa2c.android.cifsdocumentsprovider.domain.repository.CifsRepository
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

/**
 * CIFS DocumentsProvider
 */
class CifsDocumentsProvider : DocumentsProvider() {

    /** Context */
    private val providerContext: Context by lazy { context!! }
    /** App Preferences */
    private val appPreferences: AppPreferences by lazy { AppPreferences(providerContext) }
    /** Storage Manager */
    private val storageManager: StorageManager by lazy { AppModule.provideStorageManager(providerContext) }

    /** Cifs Repository */
    private val cifsRepository: CifsRepository by lazy {
        CifsRepository(CifsClient(), appPreferences, storageManager)
    }

    /** Handler thread */
    private var handlerThread: HandlerThread? = null


    override fun onCreate(): Boolean {
        return true
    }

    override fun queryRoots(projection: Array<String>?): Cursor {
        // Add root columns
        return MatrixCursor(projection.toRootProjection()).also {
            it.newRow().apply {
                add(DocumentsContract.Root.COLUMN_ROOT_ID, URI_AUTHORITY)
                add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID)
                add(DocumentsContract.Root.COLUMN_TITLE, providerContext.getString(R.string.app_name))
                add(DocumentsContract.Root.COLUMN_SUMMARY, providerContext.getString(R.string.app_summary))
                add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
                add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, Int.MAX_VALUE)
                add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
                add(DocumentsContract.Root.COLUMN_FLAGS,
                    DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD or
                            DocumentsContract.Root.FLAG_SUPPORTS_CREATE
                )
            }
        }
    }

    override fun queryDocument(documentId: String?, projection: Array<String>?): Cursor {
        val cursor = MatrixCursor(projection.toProjection())
        if (documentId.isRoot()) {
            // Root
            includeRoot(cursor)
        } else {
            // File / Directory
            runBlocking {
                documentId?.let {
                    val uri = getCifsUri(it)
                    val file = cifsRepository.getFile(uri) ?: return@let
                    includeFile(cursor, file)
                }
            }
        }
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(projection.toProjection())
        if (parentDocumentId.isRoot()) {
            runBlocking {
                cifsRepository.loadConnection().forEach { connection ->
                    try {
                        val file = cifsRepository.getFile(connection) ?: return@forEach
                        includeFile(cursor, file, connection.name)
                    } catch (e: Exception) {
                        logE(e)
                    }
                }
            }
        } else {
            runBlocking {
                val uri = getCifsDirectoryUri(parentDocumentId!!)
                cifsRepository.getFileChildren(uri).forEach { file ->
                    try {
                        includeFile(cursor, file)
                    } catch (e: Exception) {
                        logE(e)
                    }
                }
            }
        }
        return cursor
    }

    override fun isChildDocument(parentDocumentId: String?, documentId: String?): Boolean {
        val parent = if (parentDocumentId.isRoot()) "/" else parentDocumentId ?: return false
        val child = documentId ?: return false
        return child.indexOf(parent) == 0
    }

    override fun getDocumentType(documentId: String?): String {
        return documentId.mimeType
    }

    override fun openDocumentThumbnail(
        documentId: String?,
        sizeHint: Point?,
        signal: CancellationSignal?
    ): AssetFileDescriptor? {
        return null
    }

    override fun openDocument(
        documentId: String?,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor? {
        val uri = documentId?.let { getCifsFileUri(it) } ?: return null
        val accessMode = AccessMode.fromSafMode(mode)
        val thread = HandlerThread(this.javaClass.simpleName).also {
            it.start()
            handlerThread = it
        }
        return runBlocking { cifsRepository.getFileDescriptor(uri, accessMode, thread) }
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String?,
        displayName: String
    ): String? {
        val documentId = Paths.get(parentDocumentId, displayName).toString()
        val uri = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            getCifsDirectoryUri(documentId)
        } else {
            getCifsFileUri(documentId)
        }
        val cifsFile = runBlocking {
            cifsRepository.createFile(uri, mimeType)
        }
        return cifsFile?.getDocumentId()
    }

    override fun deleteDocument(documentId: String?) {
        documentId?.let {
            runBlocking {
                cifsRepository.deleteFile(getCifsUri(it))
            }
        } ?: let {
            throw OperationCanceledException()
        }
    }

    override fun renameDocument(documentId: String?, displayName: String?): String? {
        if (documentId == null || displayName == null) return null
        val targetFile = runBlocking {
            cifsRepository.renameFile(getCifsFileUri(documentId), displayName)
        }
        return targetFile?.getDocumentId()
    }

    override fun copyDocument(sourceDocumentId: String?, targetParentDocumentId: String?): String? {
        if (sourceDocumentId == null || targetParentDocumentId == null) return null
        val targetFile = runBlocking {
            cifsRepository.copyFile(getCifsFileUri(sourceDocumentId), getCifsFileUri(targetParentDocumentId))
        }
        return targetFile?.getDocumentId()
    }

    override fun moveDocument(
        sourceDocumentId: String?,
        sourceParentDocumentId: String?,
        targetParentDocumentId: String?
    ): String? {
        if (sourceDocumentId == null || targetParentDocumentId == null) return null
        val targetFile = runBlocking {
            cifsRepository.moveFile(getCifsFileUri(sourceDocumentId), getCifsFileUri(targetParentDocumentId))
        }
        return targetFile?.getDocumentId()
    }

    override fun removeDocument(documentId: String?, parentDocumentId: String?) {
        deleteDocument(documentId)
    }

    override fun shutdown() {
        handlerThread?.let {
            it.quit()
            handlerThread = null
        }
    }

    private fun includeRoot(cursor: MatrixCursor) {
        cursor.newRow().let { row ->
            row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID)
            row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
            row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
            row.add(DocumentsContract.Document.COLUMN_SIZE, 0)
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "/")
            row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, 0)
        }
    }

    private fun includeFile(cursor: MatrixCursor, file: CifsFile?, name: String? = null) {
        cursor.newRow().let { row ->
            when {
                file == null -> {
                    // Error
                    row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, ERROR_DOCUMENT_ID)
                    row.add(DocumentsContract.Document.COLUMN_SIZE, 0)
                    row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, providerContext.getString(R.string.provider_error_message))
                    row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, 0)
                    row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, "*/*")
                    row.add(DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT)
                }
                file.isDirectory -> {
                    // Directory
                    row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, file.getDocumentId())
                    row.add(DocumentsContract.Document.COLUMN_SIZE, 0)
                    row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, name ?: file.name)
                    row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified)
                    row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                    row.add(DocumentsContract.Document.COLUMN_FLAGS,
                        DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or
                                DocumentsContract.Document.FLAG_DIR_PREFERS_LAST_MODIFIED or
                                DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                                DocumentsContract.Document.FLAG_SUPPORTS_COPY or
                                DocumentsContract.Document.FLAG_SUPPORTS_MOVE or
                                DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                                DocumentsContract.Document.FLAG_SUPPORTS_REMOVE or
                                DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                    )
                }
                else -> {
                    // File
                    row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, file.getDocumentId())
                    row.add(DocumentsContract.Document.COLUMN_SIZE, file.size)
                    row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, name ?: file.name)
                    row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified)
                    row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, file.name.mimeType)
                    row.add(DocumentsContract.Document.COLUMN_FLAGS,
                        DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or
                                DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                                DocumentsContract.Document.FLAG_SUPPORTS_COPY or
                                DocumentsContract.Document.FLAG_SUPPORTS_MOVE or
                                DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                                DocumentsContract.Document.FLAG_SUPPORTS_REMOVE or
                                DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                    )
                }
            }
        }
    }

    private fun getCifsDirectoryUri(documentId: String): String {
        val uri = "smb://$documentId"
        return uri + (if (documentId.last() == '/') "" else '/')
    }

    private fun getCifsFileUri(documentId: String): String {
        return "smb://${documentId.trim('/')}"
    }

    private fun getCifsUri(documentId: String): String {
        return "smb://${documentId}"
    }

    private fun Array<String>?.toRootProjection(): Array<String> {
        return if (this.isNullOrEmpty()) {
            DEFAULT_ROOT_PROJECTION
        } else {
            this
        }
    }

    private fun Array<String>?.toProjection(): Array<String> {
        return if (this.isNullOrEmpty()) {
            DEFAULT_DOCUMENT_PROJECTION
        } else {
            this
        }
    }

    /**
     * Get Document ID from CIFS file
     */
    private fun CifsFile.getDocumentId(): String {
        return CifsConnection.getDocumentId(this.server, this.uri.port, this.uri.pathFragment, this.isDirectory) ?: ""
    }

    /**
     * True if the document id is root.
     */
    private fun String?.isRoot(): Boolean {
        return (this.isNullOrEmpty() || this == ROOT_DOCUMENT_ID)
    }

    companion object {

        private const val ROOT_DOCUMENT_ID = "/"
        private const val ERROR_DOCUMENT_ID = "////"

        private val DEFAULT_ROOT_PROJECTION: Array<String> = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
        )

        private val DEFAULT_DOCUMENT_PROJECTION: Array<String> = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )

    }
}
