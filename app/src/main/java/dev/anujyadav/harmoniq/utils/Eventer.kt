package dev.anujyadav.harmoniq.utils

import java.util.concurrent.CopyOnWriteArrayList

typealias EventSubscriber<T> = (T) -> Unit
typealias EventUnsubscribeFn = () -> Unit

class Eventer<T> {
    // events can come from any thread, like the player's timer, while something subscribes
    private val subscribers = CopyOnWriteArrayList<EventSubscriber<T>>()

    fun subscribe(subscriber: EventSubscriber<T>): EventUnsubscribeFn {
        subscribers.add(subscriber)
        return { unsubscribe(subscriber) }
    }

    fun unsubscribe(subscriber: EventSubscriber<T>) {
        subscribers.remove(subscriber)
    }

    fun dispatch(event: T) {
        subscribers.forEach { it(event) }
    }
}
