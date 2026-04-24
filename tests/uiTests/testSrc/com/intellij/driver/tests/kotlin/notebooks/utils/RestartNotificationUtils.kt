package com.intellij.driver.tests.kotlin.notebooks.utils

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.UIComponentsList.Companion.waitAny
import com.intellij.driver.sdk.ui.components.UIComponentsList.Companion.waitNotFound
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.xQuery
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val RESTART_KERNEL_TIMEOUT = 30.seconds

private fun QueryBuilder.restartNotificationLocator(): String = componentWithChild(
  componentLocator = byClass("MyComponent"),
  childLocator = contains(byVisibleText("Restarting the kernel session.")),
)

internal fun Finder.waitForKernelRestartNotification(timeout: Duration = RESTART_KERNEL_TIMEOUT) =
  waitAny(timeout = timeout) { restartNotificationLocator() }.first()

internal fun Finder.waitForAndCloseKernelRestartNotification(timeout: Duration = RESTART_KERNEL_TIMEOUT) {
  waitForKernelRestartNotification()
  x(xQuery { restartNotificationLocator() } + "/following-sibling::div[@myicon='close.svg']")
    .waitFound(timeout)
    .click()
  assertNoKernelRestartNotification()
}

internal fun Finder.assertNoKernelRestartNotification() = waitNotFound { restartNotificationLocator() }