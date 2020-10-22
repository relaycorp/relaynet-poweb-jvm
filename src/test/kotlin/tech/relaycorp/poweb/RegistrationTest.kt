package tech.relaycorp.poweb

import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.relaycorp.relaynet.bindings.pdc.ServerBindingException
import tech.relaycorp.relaynet.messages.InvalidMessageException
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistration
import tech.relaycorp.relaynet.testing.CertificationPath
import tech.relaycorp.relaynet.testing.KeyPairSet
import java.nio.charset.Charset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("RedundantInnerClassModifier")
@ExperimentalCoroutinesApi
@KtorExperimentalAPI
class RegistrationTest {
    @Nested
    inner class PreRegistration {
        private val publicKey = KeyPairSet.PRIVATE_GW.public
        private val responseHeaders =
            headersOf("Content-Type", PoWebContentType.REGISTRATION_AUTHORIZATION.value)

        @Test
        fun `Request method should be POST`() = runBlockingTest {
            var method: HttpMethod? = null
            val client = makeTestClient { request: HttpRequestData ->
                method = request.method
                respond(byteArrayOf(), headers = responseHeaders)
            }

            client.use { client.preRegisterNode(publicKey) }

            assertEquals(HttpMethod.Post, method)
        }

        @Test
        fun `Request should be made to the appropriate endpoint`() = runBlockingTest {
            var endpointURL: String? = null
            val client = makeTestClient { request: HttpRequestData ->
                endpointURL = request.url.toString()
                respond(byteArrayOf(), headers = responseHeaders)
            }

            client.use { client.preRegisterNode(publicKey) }

            assertEquals("${client.baseURL}/pre-registrations", endpointURL)
        }

        @Test
        fun `Request Content-Type should be plain text`() = runBlockingTest {
            var contentType: ContentType? = null
            val client = makeTestClient { request: HttpRequestData ->
                contentType = request.body.contentType
                respond(byteArrayOf(), headers = responseHeaders)
            }

            client.use { client.preRegisterNode(publicKey) }

            assertEquals(PoWebContentType.PRE_REGISTRATION.value, contentType.toString())
        }

        @Test
        fun `Request body should be SHA-256 digest of the node public key`() = runBlockingTest {
            var requestBody: ByteArray? = null
            val client = makeTestClient { request: HttpRequestData ->
                assertTrue(request.body is OutgoingContent.ByteArrayContent)
                requestBody = (request.body as OutgoingContent.ByteArrayContent).bytes()
                respond(byteArrayOf(), headers = responseHeaders)
            }

            client.use { client.preRegisterNode(publicKey) }

            assertEquals(
                getSHA256DigestHex(publicKey.encoded),
                requestBody!!.toString(Charset.defaultCharset())
            )
        }

        @Test
        fun `An invalid response Content-Type should be refused`() {
            val invalidContentType = ContentType.Application.Json
            val client = makeTestClient {
                respond(
                    "{}",
                    headers = headersOf("Content-Type", invalidContentType.toString())
                )
            }

            val exception = assertThrows<ServerBindingException> {
                runBlockingTest {
                    client.use { client.preRegisterNode(publicKey) }
                }
            }

            assertEquals(
                "The server returned an invalid Content-Type ($invalidContentType)",
                exception.message
            )
        }

        @Test
        fun `Registration request should be output if pre-registration succeeds`() =
            runBlockingTest {
                val authorizationSerialized = "This is the PNRA".toByteArray()
                val client = makeTestClient {
                    respond(authorizationSerialized, headers = responseHeaders)
                }

                client.use {
                    val registrationRequest = it.preRegisterNode(publicKey)
                    assertEquals(publicKey, registrationRequest.privateNodePublicKey)
                    assertEquals(
                        authorizationSerialized.asList(),
                        registrationRequest.pnraSerialized.asList()
                    )
                }
            }
    }

    @Nested
    inner class Registration {
        private val pnrrSerialized = "The PNRR".toByteArray()
        private val responseHeaders =
            headersOf("Content-Type", PoWebContentType.REGISTRATION.value)

        private val registration =
            PrivateNodeRegistration(CertificationPath.PRIVATE_GW, CertificationPath.PUBLIC_GW)
        private val registrationSerialized = registration.serialize()

        @Test
        fun `Request method should be POST`() = runBlockingTest {
            var method: HttpMethod? = null
            val client = makeTestClient { request: HttpRequestData ->
                method = request.method
                respond(registrationSerialized, headers = responseHeaders)
            }

            client.use { client.registerNode(pnrrSerialized) }

            assertEquals(HttpMethod.Post, method)
        }

        @Test
        fun `Request should be made to the appropriate endpoint`() = runBlockingTest {
            var endpointURL: String? = null
            val client = makeTestClient { request: HttpRequestData ->
                endpointURL = request.url.toString()
                respond(registrationSerialized, headers = responseHeaders)
            }

            client.use { client.registerNode(pnrrSerialized) }

            assertEquals("${client.baseURL}/nodes", endpointURL)
        }

        @Test
        fun `Request Content-Type should be a PNRR`() = runBlockingTest {
            var contentType: ContentType? = null
            val client = makeTestClient { request: HttpRequestData ->
                contentType = request.body.contentType
                respond(registrationSerialized, headers = responseHeaders)
            }

            client.use { client.registerNode(pnrrSerialized) }

            assertEquals(PoWebContentType.REGISTRATION_REQUEST.value, contentType.toString())
        }

        @Test
        fun `Request body should be the PNRR serialized`() = runBlockingTest {
            var requestBody: ByteArray? = null
            val client = makeTestClient { request: HttpRequestData ->
                assertTrue(request.body is OutgoingContent.ByteArrayContent)
                requestBody = (request.body as OutgoingContent.ByteArrayContent).bytes()
                respond(registrationSerialized, headers = responseHeaders)
            }

            client.use { client.registerNode(pnrrSerialized) }

            assertEquals(pnrrSerialized.asList(), requestBody!!.asList())
        }

        @Test
        fun `An invalid response Content-Type should be refused`() {
            val invalidContentType = ContentType.Application.Json
            val client = makeTestClient {
                respond(
                    "{}",
                    headers = headersOf("Content-Type", invalidContentType.toString())
                )
            }

            val exception = assertThrows<ServerBindingException> {
                runBlockingTest {
                    client.use { client.registerNode(pnrrSerialized) }
                }
            }

            assertEquals(
                "The server returned an invalid Content-Type ($invalidContentType)",
                exception.message
            )
        }

        @Test
        fun `An invalid registration should be refused`() {
            val client = makeTestClient {
                respond("{}", headers = responseHeaders)
            }

            val exception = assertThrows<ServerBindingException> {
                runBlockingTest {
                    client.use { client.registerNode(pnrrSerialized) }
                }
            }

            assertEquals("The server returned a malformed registration", exception.message)
            assertTrue(exception.cause is InvalidMessageException)
        }

        @Test
        fun `Registration should be output if request succeeds`() = runBlockingTest {
            val client = makeTestClient {
                respond(registrationSerialized, headers = responseHeaders)
            }

            client.use {
                val finalRegistration = it.registerNode(pnrrSerialized)
                assertEquals(
                    registration.privateNodeCertificate,
                    finalRegistration.privateNodeCertificate
                )
                assertEquals(
                    registration.gatewayCertificate,
                    finalRegistration.gatewayCertificate
                )
            }
        }
    }
}
