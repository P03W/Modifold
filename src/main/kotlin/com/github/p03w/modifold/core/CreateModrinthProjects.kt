package com.github.p03w.modifold.core

import com.github.p03w.modifold.cli.log
import com.github.p03w.modifold.cli.withSpinner
import com.github.p03w.modifold.curseforge_api.CurseforgeAPI
import com.github.p03w.modifold.curseforge_schema.CurseforgeProject
import com.github.p03w.modifold.modrinth_schema.ModrinthProject
import com.github.p03w.modifold.modrinth_api.ModrinthAPI
import com.github.p03w.modifold.modrinth_api.ModrinthProjectCreate

fun createModrinthProjects(curseforgeProjects: List<CurseforgeProject>): MutableMap<CurseforgeProject, ModrinthProject> {
    log("Creating modrinth projects from curseforge projects")
    val out = mutableMapOf<CurseforgeProject, ModrinthProject>()

    curseforgeProjects.forEach { project ->
        withSpinner("Making modrinth project for ${project.display()}") {
            val mod = ModrinthAPI.makeProject(ModrinthProjectCreate.of(project, CurseforgeAPI.getProjectDescription(project.id)), project)
            out[project] = mod
        }
    }

    return out
}
