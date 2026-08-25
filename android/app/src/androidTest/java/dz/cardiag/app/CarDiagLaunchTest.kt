package dz.cardiag.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarDiagLaunchTest {
    @get:Rule
    val activityRule = ActivityTestRule(CarDiagModernActivity::class.java)

    @Test
    fun launchActivity_doesNotCrash() {
        check(!activityRule.activity.isFinishing)
    }
}
