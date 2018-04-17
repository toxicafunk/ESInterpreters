package free

import events.{EventStore, InMemoryEventStore}

package object multi {
  implicit val eventLog: EventStore[String] = InMemoryEventStore.apply[String]
}
