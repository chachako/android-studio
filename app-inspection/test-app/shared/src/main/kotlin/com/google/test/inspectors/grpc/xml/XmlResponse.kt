package com.google.test.inspectors.grpc.xml

data class XmlResponse(val message: String, val items: List<Item>) {
  data class Item(val name: String)
}
