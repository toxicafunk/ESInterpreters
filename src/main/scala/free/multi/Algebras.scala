package free.multi

import cats.data.EitherK
import free.multi.algebras.{MessagingAlgebra, OrdersAlgebra}

object Algebras {
  type MessagingAndOrdersAlg[T] = EitherK[MessagingAlgebra, OrdersAlgebra, T]
}

