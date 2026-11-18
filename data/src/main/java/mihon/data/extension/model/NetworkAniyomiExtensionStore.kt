package mihon.data.extension.model

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.data.extension.model.NetworkExtensionStore.ContentWarning

/**
 * Protobuf representation matching Aniyomi's `extensions-lib` `index.proto`, where
 * [Extension.isTorrent] occupies field 8, [Extension.sources] occupies field 9, and
 * [Source.message] occupies field 6.
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
internal data class NetworkAniyomiExtensionStore(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val badgeLabel: String,
    @ProtoNumber(3) val signingKey: String,
    @ProtoNumber(4) val contact: NetworkExtensionStore.Contact,
    @ProtoNumber(101) val extensionList: ExtensionList? = null,
    @ProtoNumber(102) val extensionListUrl: String? = null,
) {
    @Serializable
    data class ExtensionList(@ProtoNumber(1) val extensions: List<Extension> = emptyList()) {
        fun toNetworkExtensionList(): NetworkExtensionStore.ExtensionList {
            return NetworkExtensionStore.ExtensionList(
                extensions = extensions.map { it.toNetworkExtension() },
            )
        }
    }

    @Serializable
    data class Extension(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val packageName: String,
        @ProtoNumber(3) val resources: NetworkExtensionStore.Resources,
        @ProtoNumber(4) val extensionLib: String,
        @ProtoNumber(5) val versionCode: Long,
        @ProtoNumber(6) val versionName: String,
        @ProtoNumber(7) val contentWarning: ContentWarning = ContentWarning.UNSPECIFIED,
        @ProtoNumber(8) val isTorrent: Boolean = false,
        @ProtoNumber(9) val sources: List<Source> = emptyList(),
    ) {
        fun toNetworkExtension(): NetworkExtensionStore.Extension {
            return NetworkExtensionStore.Extension(
                name = name,
                packageName = packageName,
                resources = resources,
                extensionLib = extensionLib,
                versionCode = versionCode,
                versionName = versionName,
                contentWarning = contentWarning,
                sources = sources.map { it.toNetworkSource() },
                isTorrent = isTorrent,
            )
        }
    }

    @Serializable
    data class Source(
        @ProtoNumber(1) val id: Long,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(3) val language: String,
        @ProtoNumber(4) val homeUrl: String = "",
        @ProtoNumber(5) val mirrorUrls: List<String> = emptyList(),
        @ProtoNumber(6) val message: String? = null,
    ) {
        fun toNetworkSource(): NetworkExtensionStore.Source {
            return NetworkExtensionStore.Source(
                id = id,
                name = name,
                language = language,
                homeUrl = homeUrl,
                mirrorUrls = mirrorUrls,
                message = message,
            )
        }
    }

    fun toNetworkExtensionStore(): NetworkExtensionStore {
        return NetworkExtensionStore(
            name = name,
            badgeLabel = badgeLabel,
            signingKey = signingKey,
            contact = contact,
            extensionList = extensionList?.toNetworkExtensionList(),
            extensionListUrl = extensionListUrl,
        )
    }
}
