package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.SecurityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FocusPilot", appName)
  }

  @Test
  fun `test security manager safe file rules`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val securityManager = SecurityManager(context)

    // Allowed file
    val okResult = securityManager.isFileSafeToExpose("my_notes.txt", 1024L)
    assertTrue(okResult is SecurityManager.SecurityCheckResult.Allowed)

    // Blocked secret files
    val envResult = securityManager.isFileSafeToExpose(".env", 500L)
    assertTrue(envResult is SecurityManager.SecurityCheckResult.Blocked)

    val keyResult = securityManager.isFileSafeToExpose("id_rsa", 1000L)
    assertTrue(keyResult is SecurityManager.SecurityCheckResult.Blocked)

    // Blocked large file (> 10MB)
    val largeResult = securityManager.isFileSafeToExpose("dump.bin", 15L * 1024 * 1024)
    assertTrue(largeResult is SecurityManager.SecurityCheckResult.Blocked)
  }
}
