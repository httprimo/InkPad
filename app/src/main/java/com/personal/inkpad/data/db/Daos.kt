package com.personal.inkpad.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY name ASC")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity)

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE folderId IS NULL ORDER BY updatedAt DESC")
    fun observeUnfiled(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun observeInFolder(folderId: String): Flow<List<NotebookEntity>>

    @Query(
        """
        SELECT * FROM notebooks
        WHERE title LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        """
    )
    fun search(query: String): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getById(id: String): NotebookEntity?

    @Query("SELECT * FROM notebooks WHERE id = :id")
    fun observeById(id: String): Flow<NotebookEntity?>

    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC")
    suspend fun getAll(): List<NotebookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notebook: NotebookEntity)

    @Update
    suspend fun update(notebook: NotebookEntity)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE notebookId = :notebookId ORDER BY pageIndex ASC")
    fun observeForNotebook(notebookId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE notebookId = :notebookId ORDER BY pageIndex ASC")
    suspend fun getForNotebook(notebookId: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getById(id: String): PageEntity?

    @Query("SELECT * FROM pages")
    suspend fun getAll(): List<PageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(page: PageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(pages: List<PageEntity>)

    @Update
    suspend fun update(page: PageEntity)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COALESCE(MAX(pageIndex), -1) FROM pages WHERE notebookId = :notebookId")
    suspend fun maxIndex(notebookId: String): Int
}

@Dao
interface StrokeDao {
    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY createdAt ASC")
    fun observeForPage(pageId: String): Flow<List<StrokeEntity>>

    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY createdAt ASC")
    suspend fun getForPage(pageId: String): List<StrokeEntity>

    @Query("SELECT * FROM strokes")
    suspend fun getAll(): List<StrokeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stroke: StrokeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(strokes: List<StrokeEntity>)

    @Query("DELETE FROM strokes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM strokes WHERE pageId = :pageId")
    suspend fun deleteForPage(pageId: String)

    @Query("DELETE FROM strokes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Dao
interface TextObjectDao {
    @Query("SELECT * FROM text_objects WHERE pageId = :pageId")
    fun observeForPage(pageId: String): Flow<List<TextObjectEntity>>

    @Query("SELECT * FROM text_objects WHERE pageId = :pageId")
    suspend fun getForPage(pageId: String): List<TextObjectEntity>

    @Query("SELECT * FROM text_objects")
    suspend fun getAll(): List<TextObjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(obj: TextObjectEntity)

    @Query("DELETE FROM text_objects WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ImageObjectDao {
    @Query("SELECT * FROM image_objects WHERE pageId = :pageId")
    fun observeForPage(pageId: String): Flow<List<ImageObjectEntity>>

    @Query("SELECT * FROM image_objects WHERE pageId = :pageId")
    suspend fun getForPage(pageId: String): List<ImageObjectEntity>

    @Query("SELECT * FROM image_objects")
    suspend fun getAll(): List<ImageObjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(obj: ImageObjectEntity)

    @Query("DELETE FROM image_objects WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ShapeObjectDao {
    @Query("SELECT * FROM shape_objects WHERE pageId = :pageId")
    fun observeForPage(pageId: String): Flow<List<ShapeObjectEntity>>

    @Query("SELECT * FROM shape_objects WHERE pageId = :pageId")
    suspend fun getForPage(pageId: String): List<ShapeObjectEntity>

    @Query("SELECT * FROM shape_objects")
    suspend fun getAll(): List<ShapeObjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(obj: ShapeObjectEntity)

    @Query("DELETE FROM shape_objects WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TapeMaskDao {
    @Query("SELECT * FROM tape_masks WHERE pageId = :pageId")
    fun observeForPage(pageId: String): Flow<List<TapeMaskEntity>>

    @Query("SELECT * FROM tape_masks WHERE pageId = :pageId")
    suspend fun getForPage(pageId: String): List<TapeMaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mask: TapeMaskEntity)

    @Query("DELETE FROM tape_masks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE tape_masks SET revealed = 1 WHERE pageId = :pageId")
    suspend fun revealAll(pageId: String)
}
