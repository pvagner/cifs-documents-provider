package com.wa2c.android.cifsdocumentsprovider.domain.usecase

import android.net.Uri
import com.wa2c.android.cifsdocumentsprovider.common.utils.logE
import com.wa2c.android.cifsdocumentsprovider.common.utils.logW
import com.wa2c.android.cifsdocumentsprovider.data.CifsClient
import com.wa2c.android.cifsdocumentsprovider.data.preference.PreferencesRepository
import com.wa2c.android.cifsdocumentsprovider.domain.model.*
import jcifs.CIFSContext
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("BlockingMethodInNonBlockingContext")
@Singleton
class CifsUseCase @Inject constructor(
    private val cifsClient: CifsClient,
    private val preferencesRepository: PreferencesRepository
) {
    /** CIFS Connection buffer */
    private val _connections: MutableList<CifsConnection> by lazy {
        preferencesRepository.cifsSettings.map { it.toModel() }.toMutableList()
    }

    /** CIFS Context cache */
    private val contextCache = CifsContextCache()
    /** SMB File cache */
    private val smbFileCache = SmbFileCache()
    /** CIFS File cache */
    private val cifsFileCache = CifsFileCache()



    /**
     * Get CIFS Context
     */
    private fun getCifsContext(connection: CifsConnection): CIFSContext {
        return contextCache[connection] ?: cifsClient.getConnection(connection.user, connection.password, connection.domain).also {
            contextCache.put(connection, it)
        }
    }

    /**
     * Provide connection list
     */
    fun provideConnections(): List<CifsConnection> {
        return _connections
    }

    /**
     * Save connection
     */
    fun saveConnection(connection: CifsConnection) {
        _connections.indexOfFirst { it.id == connection.id }.let { index ->
            if (index >= 0) {
                _connections[index] = connection
            } else {
                _connections.add(connection)
            }
        }
        preferencesRepository.cifsSettings = _connections.map { it.toData() }
    }

    /**
     * Delete connection
     */
    fun deleteConnection(id: Long) {
        _connections.removeIf { it.id == id }
        preferencesRepository.cifsSettings = _connections.map { it.toData() }
    }

    /**
     * Get CIFS File from connection.
     */
    suspend fun getCifsFile(connection: CifsConnection): CifsFile? {
        return cifsFileCache.get(connection) ?: getSmbFile(connection)?.toCifsFile()
    }

    /**
     * Get CIFS File from uri.
     */
    suspend fun getCifsFile(uri: String): CifsFile? {
        return  cifsFileCache.get(uri) ?: getSmbFile(uri)?.toCifsFile()
    }

    /**
     * Get children CIFS files from uri.
     */
    suspend fun getCifsFileChildren(uri: String): List<CifsFile> {
        return withContext(Dispatchers.IO) {
            try {
                getSmbFile(uri)?.listFiles()?.mapNotNull {
                    smbFileCache.get(it.url) ?: smbFileCache.put(it.url, it)
                    it.toCifsFile()
                } ?: emptyList()
            } catch (e: Exception) {
                logW(e)
                emptyList()
            }
        }
    }

    /**
     * Create new file.
     */
    suspend fun createCifsFile(uri: String): Boolean {
        return withContext(Dispatchers.IO) {
            getSmbFile(uri)?.let {
                it.createNewFile()
                true
            } ?: false
        }
    }

    /**
     * Delete a file.
     */
    suspend fun deleteCifsFile(uri: String): Boolean {
        return withContext(Dispatchers.IO) {
            getSmbFile(uri)?.let {
                it.delete()
                true
            } ?: false
        }
    }


    /**
     * Check setting connectivity.
     */
    suspend fun checkConnection(connection: CifsConnection): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                cifsClient.getFile(connection.connectionUri, getCifsContext(connection))?.exists() ?: false
            } catch (e: Exception) {
                logW(e)
                false
            }
        }
    }

    suspend fun getSmbFile(connection: CifsConnection): SmbFile? {
        return smbFileCache[connection] ?:withContext(Dispatchers.IO) {
            try {
                cifsClient.getFile(connection.connectionUri, getCifsContext(connection)).also {
                    smbFileCache.put(connection, it)
                }
            } catch (e: Exception) {
                logE(e)
                null
            }
        }
    }

    suspend fun getSmbFile(uri: String): SmbFile? {
        return smbFileCache[uri] ?: withContext(Dispatchers.IO) {
            val uriHost = try {
                Uri.parse(uri).host
            } catch (e: Exception) {
                return@withContext null
            }
            _connections.firstOrNull { it.host == uriHost }?.let {
                cifsClient.getFile(uri, getCifsContext(it)).also { file ->
                    smbFileCache.put(uri, file)
                }
            }
        }
    }

    /**
     * Convert SmbFile to CifsFile
     */
    private suspend fun SmbFile.toCifsFile(): CifsFile? {
        val urlText = url.toString()
        return cifsFileCache.get(urlText) ?: withContext(Dispatchers.IO) {
            CifsFile(
                name = name.trim('/'),
                server = server,
                uri = Uri.parse(urlText),
                size = length(),
                lastModified = lastModified,
                isDirectory = urlText.lastOrNull() == '/'
            ).let {
                cifsFileCache.put(urlText, it)
                it
            }
        }
    }

}
