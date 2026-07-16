package com.sdevprem.runtrack.data.repository

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sdevprem.runtrack.data.db.RunTrackDB
import com.sdevprem.runtrack.data.model.CompletedRunBundle
import com.sdevprem.runtrack.data.model.Run
import com.sdevprem.runtrack.data.model.RunAiArtifact
import com.sdevprem.runtrack.data.model.RunMetricsEntity
import com.sdevprem.runtrack.data.model.RunNewsHistoryEntity
import com.sdevprem.runtrack.data.storage.RunImageStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompletedRunTransactionInstrumentedTest {
    private lateinit var db: RunTrackDB
    private lateinit var repository: AppRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, RunTrackDB::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(
            db = db,
            runImageStore = RunImageStore(context),
            runDao = db.getRunDao(),
            runAiDao = db.getRunAiDao(),
            runMetricsDao = db.getRunMetricsDao(),
            runNewsHistoryDao = db.getRunNewsHistoryDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun completedRunWritesBriefStateAndCascadeDeletesHistory() = runBlocking {
        val runId = repository.insertCompletedRun(
            CompletedRunBundle(
                run = Run(img = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)),
                aiArtifact = RunAiArtifact(runId = 0),
                metrics = RunMetricsEntity(runId = 0),
                newsHistory = listOf(
                    RunNewsHistoryEntity(
                        runId = 0,
                        title = "测试新闻",
                        source = "测试来源",
                        publishedAtEpochMs = null,
                        articleUrl = "https://example.com/1",
                        playedAtEpochMs = 1L,
                        briefText = "测试简报",
                        completed = true
                    )
                )
            )
        ).toInt()

        val history = db.getRunNewsHistoryDao().observeByRunId(runId).first()
        assertEquals("测试简报", history.single().briefText)
        assertEquals(true, history.single().completed)

        db.openHelper.writableDatabase.execSQL("DELETE FROM running_table WHERE id = $runId")
        assertEquals(0, db.getRunNewsHistoryDao().observeByRunId(runId).first().size)
    }
}
