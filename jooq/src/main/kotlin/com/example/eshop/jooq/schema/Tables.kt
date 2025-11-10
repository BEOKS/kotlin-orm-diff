package com.example.eshop.jooq.schema

object Tables {
    const val CUSTOMER = "customer"
    const val PRODUCT = "product"
    const val ORDER = "orders"
    const val ORDER_ITEM = "order_item"
    const val PAYMENT = "payment"
}

object CustomerFields {
    const val ID = "id"
    const val NAME = "name"
    const val EMAIL = "email"
    const val ADDRESS = "address"
    const val REGISTERED_DATE = "registered_date"
}

object ProductFields {
    const val ID = "id"
    const val NAME = "name"
    const val PRICE = "price"
    const val STOCK = "stock"
    const val CATEGORY = "category"
}

object OrderFields {
    const val ID = "id"
    const val CUSTOMER_ID = "customer_id"
    const val ORDER_DATE = "order_date"
    const val TOTAL_AMOUNT = "total_amount"
    const val STATUS = "status"
}

object OrderItemFields {
    const val ID = "id"
    const val ORDER_ID = "order_id"
    const val PRODUCT_ID = "product_id"
    const val QUANTITY = "quantity"
    const val PRICE = "price"
}

object PaymentFields {
    const val ID = "id"
    const val ORDER_ID = "order_id"
    const val AMOUNT = "amount"
    const val PAYMENT_DATE = "payment_date"
    const val METHOD = "method"
    const val STATUS = "status"
}

