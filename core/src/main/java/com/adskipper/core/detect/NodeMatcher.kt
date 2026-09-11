package com.adskipper.core.detect

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

/**
 * L1: scan the accessibility node tree for a "skip" button (<10ms).
 */
object NodeMatcher {

    fun findSkipNode(
        root: AccessibilityNodeInfo?,
        keywords: Collection<String>,
        excluded: Collection<String> = emptySet(),
    ): Rect? {
        root ?: return null
        // A keyword match whose bounds cover (nearly) the whole screen is
        // never the skip button — e.g. Douban's `id/skip` is a full-screen,
        // text-less container; tapping its center hits the ad click-through
        // area. Real skip buttons are small.
        val rootBounds = Rect().also { root.getBoundsInScreen(it) }
        val maxArea = rootBounds.width() * rootBounds.height() / 3
        val clickableHits = ArrayList<Rect>()
        val otherHits = ArrayList<Rect>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser) {
                val textMatch =
                    KeywordMatcher.matches(node.text?.toString(), keywords, excluded)
                        ?.takeIf { KeywordMatcher.isPlausibleButtonText(node.text?.toString()) }
                val descMatch =
                    KeywordMatcher.matches(node.contentDescription?.toString(), keywords, excluded)
                        ?.takeIf { KeywordMatcher.isPlausibleButtonText(node.contentDescription?.toString()) }
                val idMatch = KeywordMatcher.matches(
                    node.viewIdResourceName?.substringAfterLast('/'), keywords)

                if (textMatch != null || descMatch != null || idMatch != null) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    if (!bounds.isEmpty && bounds.width() * bounds.height() <= maxArea) {
                        Timber.d(
                            "L1 match text=%s desc=%s id=%s clickable=%b bounds=%s",
                            node.text, node.contentDescription,
                            node.viewIdResourceName, node.isClickable, bounds,
                        )
                        (if (node.isClickable) clickableHits else otherHits).add(bounds)
                    }
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::addLast)
            }
        }
        // Prefer explicitly clickable nodes; a tap gesture at the bounds
        // center works even when the node itself is not marked clickable.
        return clickableHits.firstOrNull() ?: otherHits.firstOrNull()
    }

    /** True if any node in the tree (bounded by [cap]) carries an ad-SDK
     *  class or view-id fingerprint — see [AdSdkSignatures]. Splash trees
     *  are tiny, so this stays cheap. */
    fun hasAdSdkMarker(root: AccessibilityNodeInfo?, cap: Int): Boolean {        root ?: return false
        var count = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (AdSdkSignatures.matchesClassName(node.className?.toString()) ||
                AdSdkSignatures.matchesViewId(node.viewIdResourceName)
            ) return true
            if (++count >= cap) return false
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::addLast)
            }
        }
        return false
    }

    /** What a tap at (x, y) would hit in a tree right now.
     *
     *  [nodeAtPoint] is the deepest visible node containing the point (null when
     *  the point belongs to no node at all); [clickable] is that node or its
     *  nearest enabled clickable ancestor, i.e. the view ACTION_CLICK would act
     *  on.
     *
     *  Both are needed because many real skip buttons are NOT marked clickable:
     *  a tap gesture at their centre still works while ACTION_CLICK refuses.
     *  The distinction that matters for safety is "a node is still there" vs
     *  "the screen moved on and nothing occupies those coordinates any more". */
    class TapHit(val nodeAtPoint: AccessibilityNodeInfo?, val clickable: AccessibilityNodeInfo?)

    /** One allocation-free tree walk (a single reusable Rect, no per-node
     *  objects) resolving [TapHit] for (x, y). Bounded by [cap] because it runs
     *  inside the detection loop. */
    fun hitTest(
        root: AccessibilityNodeInfo?,
        x: Float,
        y: Float,
        cap: Int = 400,
    ): TapHit {
        root ?: return TapHit(null, null)
        val px = x.toInt()
        val py = y.toInt()
        val bounds = Rect()
        var best: AccessibilityNodeInfo? = null
        var bestDepth = -1
        var count = 0
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            if (++count >= cap) break
            if (node.isVisibleToUser) {
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty &&
                    px >= bounds.left && px < bounds.right &&
                    py >= bounds.top && py < bounds.bottom &&
                    depth > bestDepth
                ) {
                    best = node
                    bestDepth = depth
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.addLast(it to depth + 1) }
            }
        }
        var candidate = best
        var hops = 0
        while (candidate != null && hops < 6) {
            if (candidate.isClickable && candidate.isEnabled) return TapHit(best, candidate)
            candidate = candidate.parent
            hops++
        }
        return TapHit(best, null)
    }

    /** Node count with an early exit at [cap]. Splash/ad screens are a
     *  handful of views (the ad SDK's container), while real app UI is
     *  hundreds — used to decide whether a screen can still be a splash ad
     *  after the core splash window has elapsed. */
    fun treeSize(root: AccessibilityNodeInfo?, cap: Int): Int {        root ?: return 0
        var count = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (++count >= cap) return count
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::addLast)
            }
        }
        return count
    }
}
