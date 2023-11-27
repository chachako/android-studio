package com.google.test.inspectors.grpc.xml

import com.google.test.inspectors.grpc.xml.XmlResponse.Item
import io.grpc.MethodDescriptor
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element

private const val METHOD_NAME = "doXmlRpc"
private val XML = DocumentBuilderFactory.newInstance().newDocumentBuilder()
private val TRANSFORMER = TransformerFactory.newInstance().newTransformer()

object XmlGrpc {

  const val SERVICE_NAME = "XmlService"

  val doXmlGrpcMethod: MethodDescriptor<XmlRequest, XmlResponse> =
    MethodDescriptor.newBuilder<XmlRequest, XmlResponse>()
      .setRequestMarshaller(RequestMarshaller())
      .setResponseMarshaller(ResponseMarshaller())
      .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, METHOD_NAME))
      .setType(MethodDescriptor.MethodType.UNARY)
      .build()

  private class RequestMarshaller : Marshaller<XmlRequest> {

    override fun stream(request: XmlRequest): InputStream {
      val document = XML.newDocument()
      val root = document.createElement("request")
      document.appendChild(root)
      root.setAttribute("name", request.name)
      val writer = StringWriter()
      TRANSFORMER.transform(DOMSource(document), StreamResult(writer))
      val string = writer.toString()
      return ByteArrayInputStream(string.encodeToByteArray())
    }

    override fun parse(stream: InputStream): XmlRequest {
      val document = XML.parse(stream)
      return XmlRequest(document.documentElement.getAttribute("name"))
    }
  }

  private class ResponseMarshaller : Marshaller<XmlResponse> {

    override fun stream(response: XmlResponse): InputStream {
      val document = XML.newDocument()
      document.appendChild(
        document.createElement("response").apply {
          setAttribute("message", response.message)
          response.items.forEach {
            appendChild(document.createElement("item").apply { setAttribute("name", it.name) })
          }
        }
      )
      val writer = StringWriter()
      TRANSFORMER.transform(DOMSource(document), StreamResult(writer))
      return ByteArrayInputStream(writer.toString().encodeToByteArray())
    }

    override fun parse(stream: InputStream): XmlResponse {
      val document = XML.parse(stream)
      val root = document.documentElement
      val itemList = root.getElementsByTagName("item")
      val items =
        (0 until itemList.length).map {
          val name = (itemList.item(it) as Element).getAttribute("name")
          Item(name)
        }
      return XmlResponse(root.getAttribute("message"), items)
    }
  }

  @JvmStatic
  fun main(args: Array<String>) {
    val o = XmlResponse("Foo", listOf(Item("i1"), Item("i2")))
    val marshaller = ResponseMarshaller()
    val xml = marshaller.stream(o).reader().readLines()
    println(xml)
    println(marshaller.parse(marshaller.stream(o)))
  }
}
