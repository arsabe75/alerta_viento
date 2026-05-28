package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.WindViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    assertEquals("Alerta Viento", appName)
  }

  @Test
  fun `instantiate WindViewModel`() {
    val application: Application? = try {
      ApplicationProvider.getApplicationContext<Application>()
    } catch (e: Exception) {
      null
    }
    if (application == null) {
      throw RuntimeException("APPLICATION CONTEXT IS NULL")
    }
    try {
      val vm = WindViewModel(application)
      assertNotNull(vm)
    } catch (t: Throwable) {
      val writer = java.io.StringWriter()
      t.printStackTrace(java.io.PrintWriter(writer))
      throw RuntimeException("VM_CONSTRUCTOR_CRASH: \n" + writer.toString(), t)
    }
  }
}
