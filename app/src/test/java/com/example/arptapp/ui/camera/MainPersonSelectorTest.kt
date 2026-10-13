package com.example.arptapp.ui.camera

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MainPersonSelectorTest {
    private data class Point(val x: Float, val y: Float)

    private fun select(people: List<List<Point>>): List<Point>? =
        MainPersonSelector.select(people, Point::x, Point::y)

    @Test
    fun returnsMeasurementWaitingWhenNoPersonExists() {
        assertNull(select(emptyList()))
        assertNull(select(listOf(emptyList())))
    }

    @Test
    fun retainsTheOriginalLandmarksForOnePerson() {
        val person = listOf(Point(0.2f, 0.1f), Point(0.6f, 0.9f))
        assertSame(person, select(listOf(person)))
    }

    @Test
    fun choosesLargestBoundingBoxRatherThanMostLandmarks() {
        val background = List(33) { Point(0.1f, 0.1f) } + Point(0.2f, 0.2f)
        val foreground = listOf(Point(0.1f, 0.1f), Point(0.8f, 0.9f))
        assertSame(foreground, select(listOf(background, foreground)))
    }

    @Test
    fun retainsFirstPersonWhenAreasAreEqual() {
        val first = listOf(Point(0f, 0f), Point(0.5f, 0.5f))
        val second = listOf(Point(0.25f, 0.25f), Point(0.75f, 0.75f))
        assertSame(first, select(listOf(first, second)))
    }

    @Test
    fun skipsEmptyAndNonFiniteCandidates() {
        val invalid = listOf(Point(Float.NaN, 0f), Point(0f, Float.POSITIVE_INFINITY))
        val person = listOf(Point(0f, 0f), Point(1f, 1f))
        assertSame(person, select(listOf(emptyList(), invalid, person)))
        assertNull(select(listOf(invalid)))
    }

    @Test
    fun preservesSelectionForPointsExtendingOutsideImageBounds() {
        // MediaPipe는 화면 가장자리에서 [0,1] 밖의 정규화 좌표를 반환할 수 있어 임의로 자르지 않는다.
        val wide = listOf(Point(-0.1f, 0f), Point(1.1f, 1f))
        val narrow = listOf(Point(0f, 0f), Point(1f, 1f))
        assertSame(wide, select(listOf(narrow, wide)))
    }
}
