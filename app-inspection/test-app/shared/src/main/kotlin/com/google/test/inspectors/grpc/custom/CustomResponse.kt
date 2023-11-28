package com.google.test.inspectors.grpc.custom

data class CustomResponse(val message: String, val items: List<Item>) {
  data class Item(val name: String)
}
