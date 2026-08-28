package com.mmx.order.adapter.out.integration;

import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.OpenContractPosition;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
@Tag("fast")

class PositionApiOpenPositionAdapterTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void findOpenByContractNumber_returnsPositionOn200() {
        server.createContext(
                "/contracts/C-100",
                exchange -> {
                    byte[] body =
                            """
                            {"contractNumber":"C-100","currency":"EUR","outstandingAmount":"2000000.00"}
                            """
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body);
                    }
                });

        var adapter = new PositionApiOpenPositionAdapter("http://localhost:" + port);
        Optional<OpenContractPosition> result =
                adapter.findOpenByContractNumber(new ContractNumber("C-100"));

        assertThat(result).isPresent();
        assertThat(result.get().outstandingAmount()).isEqualByComparingTo("2000000.00");
    }

    @Test
    void findOpenByContractNumber_emptyOn5xx() {
        server.createContext("/contracts/C-ERR", exchange -> exchange.sendResponseHeaders(503, -1));

        var adapter = new PositionApiOpenPositionAdapter("http://localhost:" + port);
        assertThat(adapter.findOpenByContractNumber(new ContractNumber("C-ERR"))).isEmpty();
    }
}
