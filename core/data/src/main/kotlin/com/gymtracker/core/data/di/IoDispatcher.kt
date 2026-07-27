package com.gymtracker.core.data.di

import javax.inject.Qualifier

/**
 * The dispatcher for disk and database work.
 *
 * Qualified rather than defaulted, because Hilt ignores Kotlin default parameter values on
 * `@Inject` constructors — a default would compile and then fail at graph creation.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
