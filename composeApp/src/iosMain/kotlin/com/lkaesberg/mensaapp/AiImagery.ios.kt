package com.lkaesberg.mensaapp

import platform.StoreKit.SKPaymentQueue

/** StoreKit's cached storefront; nil until StoreKit has resolved it. */
actual fun appStoreCountry(): String? = SKPaymentQueue.defaultQueue().storefront?.countryCode
