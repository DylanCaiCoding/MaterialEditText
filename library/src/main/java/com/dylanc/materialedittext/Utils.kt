package com.dylanc.materialedittext

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager

/**
 * @author Dylan Cai
 * @since 2019/10/29
 */

val Int.dp
  get() = toFloat().dp

val Float.dp
  get() = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    this,
    Resources.getSystem().displayMetrics
  )

fun View.showKeyboard() {
  val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
  if (imm != null) {
    requestFocus()
    imm.showSoftInput(this, 0)
  }
}

fun View.hideKeyboard() {
  val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
  imm?.hideSoftInputFromWindow(windowToken, 0)
}

fun View.toggleSoftInput() {
  val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
  imm?.toggleSoftInput(0, 0)
}

val Context.accentColor: Int
  get() = TypedValue().apply {
    theme.resolveAttribute(R.attr.colorAccent, this, true)
  }.data