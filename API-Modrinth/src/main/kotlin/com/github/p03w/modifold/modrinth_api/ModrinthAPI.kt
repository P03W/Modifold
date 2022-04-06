package com.github.p03w.modifold.modrinth_api

import com.github.p03w.modifold.api_core.APIInterface
import com.github.p03w.modifold.api_core.Ratelimit
import com.github.p03w.modifold.curseforge_api.CurseforgeAPI
import com.github.p03w.modifold.curseforge_schema.*
import com.google.gson.GsonBuilder
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import java.net.URL
import kotlin.time.Duration.Companion.milliseconds

object ModrinthAPI : APIInterface() {
    override val ratelimit = Ratelimit(50.milliseconds, false)
    override val ratelimitRemainingHeader = "X-Ratelimit-Remaining"
    lateinit var AuthToken: String

    override fun HttpRequestBuilder.attachAuth() {
        headers {
            append("Authorization", AuthToken)
        }
    }

    const val root = "https://api.modrinth.com/v2"

    fun getPossibleLicenses(): List<String> {
        val shortLicenses = getWithoutAuth<List<ModrinthShortLicense>>("$root/tag/license")
        return shortLicenses.map { it.short }.filterNot { it == "custom" }
    }

    fun getPossibleLoaders(): List<ModrinthLoader> {
        return getWithoutAuth("$root/tag/loader")
    }

    fun getPossibleCategories(): List<ModrinthCategory> {
        return getWithoutAuth("$root/tag/category")
    }

    fun getProjectInfo(id: String): ModrinthProject {
        return get("$root/project/$id")
    }

    fun getUser(): ModrinthUser {
        return get("$root/user")
    }

    fun getUserProjects(user: ModrinthUser): List<ModrinthProject> {
        return get<List<ModrinthProject>>("$root/user/${user.id}/projects").filter { it.project_type == "mod" }
    }

    fun makeMod(create: ModrinthModCreate, project: CurseforgeProject): ModrinthProject {
        return postForm("$root/mod") {
            append("data", GsonBuilder().serializeNulls().create().toJson(create))

            appendInput("icon", headersOf(HttpHeaders.ContentDisposition, "filename=icon.png")) {
                buildPacket {
                    writeFully(
                        URL(project.attachments.first { it.isDefault }.url)
                            .openStream()
                            .readAllBytes()
                    )
                }
            }
        }
    }

    private fun getLoaders(file: CurseforgeFile): List<String> {
        return file.gameVersion.filterNot { MC_SEMVER.matches(it) || it.lowercase().contains("java") }
            .map { it.lowercase() }
    }

    fun makeModVersion(mod: ModrinthProject, file: CurseforgeFile, project: CurseforgeProject) {
        postForm<HttpResponse>("$root/version") {
            val upload = ModrinthVersionUpload(
                mod_id = mod.id,
                file_parts = listOf("${file.fileName}-0"),
                version_number = SEMVER.find(file.fileName.removeSuffix(".jar"))?.value
                    ?: file.fileName.removeSuffix(".jar"),
                version_title = file.displayName,
                version_body = "Transferred automatically from https://www.curseforge.com/minecraft/mc-mods/${project.slug}/files/${file.id}",
                game_versions = file.gameVersion.filter { MC_SEMVER.matches(it) },
                release_channel = when (file.releaseType) {
                    3 -> "alpha"
                    2 -> "beta"
                    1 -> "release"
                    else -> throw IllegalArgumentException("Unknown release type ${file.releaseType} on file https://www.curseforge.com/minecraft/mc-mods/${project.slug}/files/${file.id}")
                },
                loaders = getLoaders(file)
            )
            append("data", GsonBuilder().serializeNulls().create().toJson(upload))

            appendInput(
                "${file.fileName}-0",
                headersOf(HttpHeaders.ContentDisposition, "filename=${file.fileName}"),
                file.fileLength
            ) {
                buildPacket {
                    writeFully(CurseforgeAPI.getFileStream(file).readAllBytes())
                }
            }
        }
    }

    @Suppress("SpellCheckingInspection")
    private val SEMVER =
        Regex("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?")

    @Suppress("SpellCheckingInspection")
    val MC_SEMVER = Regex("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:\\.(0|[1-9]\\d*))?")
}