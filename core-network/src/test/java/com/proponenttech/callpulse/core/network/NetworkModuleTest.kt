package com.proponenttech.callpulse.core.network

import com.proponenttech.callpulse.core.network.di.NetworkModule
import okhttp3.Request
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class NetworkModuleTest {
    @Test fun everyMobileRequestUsesCurrentSchoolTokenAndDeveloperHost() {
        var token: String? = "token"
        var school: String? = "school-uuid"
        val config = object : ApiConfig {
            override val baseUrl = "http://10.0.2.2:8123/api/v1/"
            override val appVersionCode = 4
            override val appVersion = "1.0.2"
            override val isDebug = false
        }
        var captured: Request? = null
        val client = NetworkModule.provideOkHttp(config,
            object : TokenProvider { override fun currentToken() = token },
            object : BaseUrlProvider { override fun currentBaseUrl() = "http://127.0.0.1:8123/" },
            object : SchoolProvider { override fun currentSchoolId() = school },
        ).newBuilder().addInterceptor { chain ->
            val request = chain.request()
            captured = request
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("{}".toResponseBody()).build()
        }.build()
        client.newCall(Request.Builder().url(config.baseUrl + "mobile/bootstrap").build()).execute().close()
        val sent = requireNotNull(captured)
        assertEquals("school-uuid", sent.header("X-School-Id"))
        assertEquals("Bearer token", sent.header("Authorization"))
        assertEquals("127.0.0.1", sent.url.host)
        assertEquals(8123, sent.url.port)
        assertEquals("/api/v1/mobile/bootstrap", sent.url.encodedPath)
        token = null
        school = null
        client.newCall(Request.Builder().url(config.baseUrl + "mobile/auth/login").build()).execute().close()
        assertNull(captured?.header("X-School-Id"))
        assertNull(captured?.header("Authorization"))
    }
}
