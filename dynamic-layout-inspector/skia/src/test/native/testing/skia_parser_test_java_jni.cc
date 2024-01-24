#include <jni.h>
#include <string>
#include "include/codec/SkPngDecoder.h"
#include "include/core/SkBitmap.h"
#include "include/core/SkFontMgr.h"
#include "include/core/SkPictureRecorder.h"
#include "include/core/SkSerialProcs.h"
#include "include/core/SkTypeface.h"
#include "include/effects/SkGradientShader.h"
#include "include/encode/SkPngEncoder.h"
#include "include/ports/SkFontMgr_empty.h"
#include "skia.grpc.pb.h"
#include "skia.pb.h"
#include "tree_building_canvas.h"

void add_requested_node(::layoutinspector::proto::GetViewTreeRequest &request,
                        int x, int y, int width, int height, int id) {
  ::layoutinspector::proto::RequestedNodeInfo *node1 =
      request.add_requested_nodes();
  node1->set_x(x);
  node1->set_y(y);
  node1->set_width(width);
  node1->set_height(height);
  node1->set_id(id);
}

sk_sp<SkImage> DeserializeImage(const void *bytes, size_t length, void *) {
  sk_sp<SkData> data = SkData::MakeWithoutCopy(bytes, length);
  const auto get_image = [](std::unique_ptr<SkCodec> codec) -> sk_sp<SkImage> {
    if (!codec) {
      return nullptr;
    }
    SkImageInfo targetInfo =
        codec->getInfo().makeAlphaType(kPremul_SkAlphaType);
    return std::get<0>(codec->getImage(targetInfo));
  };
  if (SkPngDecoder::IsPng(bytes, length)) {
    return get_image(SkPngDecoder::Decode(data, nullptr));
  }
  return nullptr;
}

static sk_sp<SkTypeface> DeserializeTypeface(const void *data, size_t length,
                                             void *ctx) {
  // TODO(bungeman,kjlubick) This should not be how the Skia deserial proc
  // works.
  SkStream *stream = *(reinterpret_cast<SkStream **>(const_cast<void *>(data)));
  if (length < sizeof(stream)) {
    return nullptr;
  }
  // Use an empty font manager to use the fonts included in the skia image.
  return SkTypeface::MakeDeserialize(stream, SkFontMgr_New_Custom_Empty());
}

jbyteArray build_tree(sk_sp<SkData> data,
                      ::layoutinspector::proto::GetViewTreeRequest request,
                      float scale, JNIEnv *env) {
  auto root = ::layoutinspector::proto::InspectorView();
  v1::TreeBuildingCanvas::ParsePicture(
      static_cast<const char *>(data->data()), data->size(), 1,
      &(request.requested_nodes()), scale, &root);

  std::string str;
  root.SerializeToString(&str);

  int size = str.length();
  jbyteArray result = env->NewByteArray(size);
  env->SetByteArrayRegion(result, 0, size, (jbyte *)(str.c_str()));
  return result;
}

extern "C" {

sk_sp<SkData> generateBoxesData() {
  SkPictureRecorder recorder;
  SkPaint paint;

  paint.setStyle(SkPaint::kFill_Style);
  paint.setAntiAlias(true);
  paint.setStrokeWidth(0);

  SkCanvas *canvas = recorder.beginRecording({0, 0, 1000, 2000});
  const SkRect &skRect1 = SkRect::MakeXYWH(0, 0, 1000, 2000);
  canvas->drawAnnotation(skRect1, "RenderNode(id=1, name='LinearLayout')",
                         nullptr);
  paint.setColor(SK_ColorYELLOW);
  canvas->drawRect(skRect1, paint);

  const SkRect &skRect2 = SkRect::MakeXYWH(0, 0, 500, 1000);
  canvas->drawAnnotation(skRect2, "RenderNode(id=2, name='FrameLayout')",
                         nullptr);
  canvas->save();
  canvas->translate(100, 100);
  paint.setColor(SK_ColorBLUE);
  canvas->drawRect(skRect2, paint);

  const SkRect &skRect3 = SkRect::MakeXYWH(0, 0, 200, 500);
  canvas->drawAnnotation(skRect3, "RenderNode(id=3, name='AppCompatButton')",
                         nullptr);
  canvas->save();
  canvas->translate(200, 200);
  paint.setColor(SK_ColorBLACK);
  canvas->drawRect(skRect3, paint);
  canvas->restore();
  canvas->drawAnnotation(skRect3, "/RenderNode(id=3, name='AppCompatButton')",
                         nullptr);

  canvas->restore();
  canvas->drawAnnotation(skRect2, "/RenderNode(id=2, name='FrameLayout')",
                         nullptr);

  const SkRect &skRect4 = SkRect::MakeXYWH(0, 0, 400, 500);
  canvas->drawAnnotation(skRect4, "RenderNode(id=4, name='Button')", nullptr);
  canvas->save();
  canvas->translate(300, 1200);
  paint.setColor(SK_ColorRED);
  canvas->drawRect(skRect4, paint);
  canvas->restore();
  canvas->drawAnnotation(skRect4, "/RenderNode(id=4, name='Button')", nullptr);

  canvas->drawAnnotation(skRect1, "/RenderNode(id=1, name='LinearLayout')",
                         nullptr);

  sk_sp<SkPicture> picture = recorder.finishRecordingAsPicture();
  SkSerialProcs sProcs;
  sProcs.fImageProc = [](SkImage *img, void *) -> sk_sp<SkData> {
    return SkPngEncoder::Encode(nullptr, img, SkPngEncoder::Options{});
  };
  return picture->serialize(&sProcs);
}

JNIEXPORT jbyteArray JNICALL
Java_com_android_tools_layoutinspector_SkiaParserTest_generateBoxes(
    JNIEnv *env, jobject instance) {
  ::layoutinspector::proto::GetViewTreeRequest request;
  add_requested_node(request, 0, 0, 1000, 2000, 1);
  add_requested_node(request, 300, 1200, 400, 500, 4);

  return build_tree(generateBoxesData(), request, 1.0, env);
}

JNIEXPORT jbyteArray JNICALL
Java_com_android_tools_layoutinspector_SkiaParserTest_generateBoxesData(
    JNIEnv *env, jobject instance) {
  sk_sp<SkData> skp = generateBoxesData();
  jbyteArray result = env->NewByteArray(skp->size());
  env->SetByteArrayRegion(result, 0, skp->size(), (const jbyte *)skp->bytes());
  return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_android_tools_layoutinspector_SkiaParserTest_generateImage(
    JNIEnv *env, jobject instance) {
  // Register the appropriate codecs for the supported image formats.
  SkCodecs::Register(SkPngDecoder::Decoder());

  SkPictureRecorder recorder;

  SkCanvas *canvas = recorder.beginRecording({0, 0, 10, 10});
  uint32_t storage[100];
  for (int i = 0; i < 100; i++) {
    storage[i] = 25 * ((i % 10) + ((i / 10) << 16)) + 0xFF000000;
  }
  SkImageInfo imageInfo =
      SkImageInfo::Make(10, 10, kBGRA_8888_SkColorType, kUnpremul_SkAlphaType);
  SkPixmap pixmap(imageInfo, storage, sizeof(storage) / 10);
  SkBitmap bitmap;
  bitmap.installPixels(pixmap);
  sk_sp<SkImage> origImage = bitmap.asImage();
  // Probably the original image is PNG already, but we reencode explicitly to
  // be sure, since we know SKPs from android will contain PNG images.
  sk_sp<SkData> data(SkPngEncoder::Encode(nullptr, origImage.get(), {}));
  sk_sp<SkImage> pngImage = SkImages::DeferredFromEncodedData(data);

  const SkRect &skRect1 = SkRect::MakeXYWH(0, 0, 10, 10);
  canvas->drawAnnotation(skRect1, "RenderNode(id=1, name='Image')", nullptr);
  canvas->drawImage(pngImage, 0, 0);

  canvas->drawAnnotation(skRect1, "/RenderNode(id=1, name='Image')", nullptr);

  sk_sp<SkPicture> picture = recorder.finishRecordingAsPicture();
  SkSerialProcs sProcs;
  sProcs.fImageProc = [](SkImage *img, void *) -> sk_sp<SkData> {
    return SkPngEncoder::Encode(nullptr, img, SkPngEncoder::Options{});
  };

  ::layoutinspector::proto::GetViewTreeRequest request;
  add_requested_node(request, 0, 0, 10, 10, 1);
  return build_tree(picture->serialize(&sProcs), request, 1.0, env);
}

JNIEXPORT jbyteArray JNICALL
Java_com_android_tools_layoutinspector_SkiaParserTest_generateTransformedViews(
    JNIEnv *env, jobject instance) {
  SkPictureRecorder recorder;
  SkPaint paint;

  paint.setStyle(SkPaint::kFill_Style);
  paint.setAntiAlias(true);
  paint.setStrokeWidth(0);

  SkCanvas *canvas = recorder.beginRecording({0, 0, 256, 256});
  canvas->drawAnnotation(SkRect::MakeXYWH(0, 0, 256, 256),
                         "RenderNode(id=1, name='Node1')", nullptr);
  canvas->drawColor(SK_ColorYELLOW);
  const SkRect &skRect1 = SkRect::MakeXYWH(0, 0, 400, 300);
  canvas->drawAnnotation(skRect1, "RenderNode(id=2, name='Transformed')",
                         nullptr);

  paint.setStyle(SkPaint::kFill_Style);
  paint.setAntiAlias(true);
  paint.setStrokeWidth(0);

  SkColor colors[] = {SK_ColorBLUE, SK_ColorRED};
  SkScalar positions[] = {0.0, 1.0};
  SkPoint pts[] = {{0, 0}, {0, 300}};

  SkMatrix matrix;
  matrix.setIdentity();
  matrix.setRotate(50);
  matrix.setPerspX(0.002);
  matrix.setPerspY(0.001);
  matrix.setTranslateX(200);
  matrix.setTranslateY(60);

  auto lgs = SkGradientShader::MakeLinear(pts, colors, positions, 2,
                                          SkTileMode::kMirror, 0, &matrix);

  paint.setShader(lgs);
  canvas->save();
  canvas->concat(matrix);
  canvas->drawRect(skRect1, paint);

  canvas->drawAnnotation(skRect1, "RenderNode(id=3, name='NestedTransform')",
                         nullptr);
  canvas->save();
  canvas->translate(200, 100);
  canvas->scale(0.3, 0.4);
  paint.setShader(nullptr);
  paint.setColor(SK_ColorBLACK);
  canvas->drawRect(SkRect::MakeXYWH(0, 0, 400, 300), paint);
  canvas->restore();
  canvas->drawAnnotation(skRect1, "/RenderNode(id=3, name='NestedTransform')",
                         nullptr);

  canvas->drawAnnotation(skRect1, "RenderNode(id=4, name='AbsoluteTransform')",
                         nullptr);
  canvas->save();
  SkM44 matrix2 = SkM44::Translate(10, 10);
  canvas->setMatrix(matrix2);
  paint.setColor(SK_ColorGREEN);
  canvas->drawCircle(10, 10, 10, paint);
  canvas->restore();
  canvas->drawAnnotation(skRect1, "/RenderNode(id=4, name='AbsoluteTransform')",
                         nullptr);

  canvas->restore();

  canvas->drawAnnotation(skRect1, "/RenderNode(id=2, name='Transformed')",
                         nullptr);
  paint.setColor(SK_ColorGREEN);
  canvas->drawRect(SkRect::MakeXYWH(100, 100, 40, 40), paint);

  canvas->drawAnnotation(skRect1, "/RenderNode(id=1, name='Node1')", nullptr);

  sk_sp<SkPicture> picture = recorder.finishRecordingAsPicture();
  SkSerialProcs sProcs;
  sProcs.fImageProc = [](SkImage *img, void *) -> sk_sp<SkData> {
    return SkPngEncoder::Encode(nullptr, img, SkPngEncoder::Options{});
  };

  ::layoutinspector::proto::GetViewTreeRequest request;
  add_requested_node(request, 0, 0, 256, 256, 1);
  add_requested_node(request, 0, 60, 254, 206, 2);
  add_requested_node(request, 98, 185, 90, 55, 3);
  add_requested_node(request, 10, 10, 20, 20, 4);

  return build_tree(picture->serialize(&sProcs), request, 0.7, env);
}

JNIEXPORT jbyteArray JNICALL
Java_com_android_tools_layoutinspector_SkiaParserTest_generateRealWorldExample(
    JNIEnv *env, jobject instance, jstring filenameStr) {
  const char *filenameChars = env->GetStringUTFChars(filenameStr, nullptr);
  std::cout << filenameChars << std::endl;
  sk_sp<SkData> data = SkData::MakeFromFileName(filenameChars);
  env->ReleaseStringUTFChars(filenameStr, filenameChars);
  SkDeserialProcs procs;
  procs.fImageProc = DeserializeImage;
  procs.fTypefaceProc = DeserializeTypeface;
  sk_sp<SkPicture> picture = SkPicture::MakeFromData(data.get(), &procs);

  ::layoutinspector::proto::GetViewTreeRequest request;
  add_requested_node(request, 0, 0, 1023, 240, 82);
  add_requested_node(request, 9, 0, 264, 213, 83);
  add_requested_node(request, 891, 162, 175, 59, 84);
  add_requested_node(request, 0, 0, 1001, 234, 81);
  add_requested_node(request, 32, 266, 937, 3404, 86);
  add_requested_node(request, 0, 234, 1001, 670, 85);
  add_requested_node(request, 872, 837, 112, 112, 87);
  add_requested_node(request, 0, 0, 1000, 904, 80);
  add_requested_node(request, 0, 0, 1000, 1000, 73);
  SkSerialProcs sProcs;
  sProcs.fImageProc = [](SkImage *img, void *) -> sk_sp<SkData> {
    return SkPngEncoder::Encode(nullptr, img, SkPngEncoder::Options{});
  };

  return build_tree(picture->serialize(&sProcs), request, 0.7, env);
}
}
