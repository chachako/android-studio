package com.android.fakeadbserver.hostcommandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import java.net.Socket

/**
 * host-prefix:killforward-all ADB command removes all port forwarding from this device. This
 * implementation only handles tcp sockets, and not Unix domain sockets.
 */
class KillForwardAllCommandHandler : SimpleHostCommandHandler("killforward-all") {

    override fun invoke(
        fakeAdbServer: FakeAdbServer,
        responseSocket: Socket,
        device: DeviceState?,
        args: String
    ): Boolean {
        assert(device != null)
        device!!.removeAllPortForwarders()
        // We send 2 OKAY answers: 1st OKAY is connect, 2nd OKAY is status.
        // See
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/adb.cpp;l=1058
        writeOkay(responseSocket.getOutputStream())
        writeOkay(responseSocket.getOutputStream())

        // We always close the connection, as per ADB protocol spec.
        return false
    }

}
