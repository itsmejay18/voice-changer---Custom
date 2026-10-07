package com.vicechanger.app

import android.app.Application

/**
 * Application entry point. Holds nothing yet beyond the process-wide settings store;
 * audio resources are owned by the UI layer's ViewModels so they are released with
 * the lifecycle instead of leaking out of a global.
 */
class ViceChangerApp : Application()
