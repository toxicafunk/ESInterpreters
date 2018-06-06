package events

import common.models.Error

import scala.collection.concurrent.TrieMap

trait EventStore[K] {
  /**
    * gets the list of events for an aggregate key `key`
    */
  def get(key: K): List[Event[_]]

  /**
    * puts a `key` and its associated `event`
    */
  def put(key: K, event: Event[_]): Either[Error, Event[_]]

  /**
    * similar to `get` but returns an error if the `key` is not found
    */
  def events(key: K): Either[Error, List[Event[_]]]

  /**
    * get all ids from the event store
    */
  def allEvents: Either[Error, List[Event[_]]]
}

/**
  * In memory store
  */
object InMemoryEventStore {
  def apply[K]: EventStore[K] = new EventStore[K] {
    val eventLog: TrieMap[K, List[Event[_]]] = TrieMap[K, List[Event[_]]]()

    def get(key: K): List[Event[_]] = eventLog.getOrElse(key, List.empty[Event[_]])

    def put(key: K, event: Event[_]): Either[Error, Event[_]] = {
      val currentList = eventLog.getOrElse(key, Nil)
      eventLog += (key -> (event :: currentList))
      Right(event)
    }

    def events(key: K): Either[Error, List[Event[_]]] = {
      val currentList = eventLog.getOrElse(key, Nil)
      if (currentList.isEmpty) Left(s"Aggregate $key does not exist")
      else Right(currentList)
    }

    def allEvents: Either[Error, List[Event[_]]] = Right(eventLog.values.toList.flatten)
  }
}

