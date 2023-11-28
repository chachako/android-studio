package com.google.test.inspectors.grpc.json

data class JsonResponse(val message: String, val items: List<Item>) {
  data class Item(val name: String)
}
