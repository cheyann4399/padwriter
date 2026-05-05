package com.aivoice.input.repository

import com.aivoice.input.db.AppDatabase
import com.aivoice.input.model.Project
import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val database: AppDatabase) {
    fun getAllProjects(): Flow<List<Project>> =
        database.projectDao().getAll()

    suspend fun getProjectById(id: Long): Project? =
        database.projectDao().getById(id)

    suspend fun createProject(name: String, premise: String): Project {
        val project = Project(
            name = name,
            premise = premise,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val id = database.projectDao().insert(project)
        return project.copy(id = id)
    }

    suspend fun updateProject(project: Project) {
        database.projectDao().update(
            project.copy(updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun deleteProject(projectId: Long) {
        database.projectDao().deleteById(projectId)
    }
}