package pl.com.bottega.erp.sales.domain

import errors.OrderOperationException
import events.{ProductAddedToOrder, OrderArchived, OrderCreated}
import pl.com.bottega.ddd.{EntityStatus, DomainEntity}
import pl.com.bottega.ddd.EntityStatus._
import pl.com.bottega.ddd.domain.sharedkernel.{newId, Money}
import java.sql.Timestamp
import policies.rebate.Rebates._

object Order {

  def apply(event: OrderCreated): Order = new Order(event.id, event.status, event.totalCost, event.items, None)
}

case class Order(override val id: Long, status: OrderStatus.Value,
                 totalCost: Money, items: List[OrderLine], submitDate: Option[Timestamp]) extends DomainEntity(id: Long) {

  def apply(archived: OrderArchived): Order = new Order(id, OrderStatus.Archived, totalCost, items, submitDate)

  def apply(productAdded: ProductAddedToOrder)(implicit client: Client, policy: RebatePolicy): Order = {
    checkIfDraft(client)
    val lineOption = items.find(item => item.id == productAdded.productid)
    val newItems = lineOption match {
      case Some(orderLine) => {
        val index = items.indexOf(orderLine)
        items.updated(index, orderLine.increaseQuantity(productAdded.quantity, policy))
      }
      case None => OrderLine(newId(), OrderProduct(productAdded), productAdded.quantity, policy) :: items
    }
    Order(id, status, totalCost, newItems, submitDate).recalculate(policy)
  }

  // keeping consistency of inner entities
  private def recalculate(policy: RebatePolicy): Order = {
    val itemsWithRebate = items.map(_.applyPolicy(policy))
    val newTotalCost = items.foldLeft(Money(0))((accumulator, item) => accumulator + item.effectiveCost)
    Order(id, status, newTotalCost, itemsWithRebate, submitDate)
  }

  private def checkIfDraft(client: Client) {
    if (status ne OrderStatus.Draft) throw new OrderOperationException("Operation allowed only in DRAFT status", client.id, id)
  }
}

object OrderLine {
  def apply(id: Long, product: OrderProduct, quantity: Int, rebatePolicy: RebatePolicy): OrderLine = {
    null
  }
}

case class OrderLine(override val id: Long, product: OrderProduct, quantity: Int, regularCost: Money, effectiveCost: Money)
  extends DomainEntity(id) {
  def applyPolicy(policy: RebatePolicy): OrderLine = {

    val regularCost = product.price * quantity
    val rebate = policy(product, quantity, regularCost)
    val newEffectiveCost = regularCost - rebate
    OrderLine(id, product, quantity, regularCost, newEffectiveCost)
  }

  def increaseQuantity(quantity: Int, policy: RebatePolicy): OrderLine = {
    this // TODO
  }
}

object OrderProduct {

  def apply(event: ProductAddedToOrder): OrderProduct =
    new OrderProduct(event.productid, event.productName, event.productType, event.price)

}

case class OrderProduct(override val id: Long, name: String, productType: ProductType.Value, price: Money) extends DomainEntity(id)