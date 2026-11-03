package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.GpsPoint
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LariKita", appName)
    }

    @Test
    fun `test GpsPoint json serialization with android platform json`() {
        val points = listOf(
            GpsPoint(-6.1754, 106.8272, 1234567, 3.5f, 2.8f),
            GpsPoint(-6.1760, 106.8280, 1234570, 4.0f, 3.0f)
        )
        val json = GpsPoint.listToJsonString(points)
        val parsed = GpsPoint.listFromJsonString(json)
        assertEquals(2, parsed.size)
        assertEquals(points[0].latitude, parsed[0].latitude, 0.0001)
        assertEquals(points[1].longitude, parsed[1].longitude, 0.0001)
    }

    @Test
    fun `test MainViewModel instantiation`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.viewmodel.MainViewModel(app)
        org.junit.Assert.assertNotNull(vm)
    }
}
