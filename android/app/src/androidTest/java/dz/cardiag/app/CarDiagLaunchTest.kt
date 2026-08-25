package dz.cardiag.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarDiagLaunchTest {
    @get:Rule
    val activityRule = ActivityTestRule(CarDiagModernActivity::class.java)

    @Test
    fun launchActivity_doesNotCrash() {
        val activity = activityRule.activity
        assertFalse("Launcher Activity is finishing", activity.isFinishing)
        assertFalse("Launcher Activity is destroyed", activity.isDestroyed)
        assertTrue("Compose content was not attached", activity.findViewById<android.view.ViewGroup>(android.R.id.content).childCount > 0)
    }
}
