package com.municipality.agent.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Refuses a request body bigger than a message could possibly be.
 *
 * <p>A message is a line of text and at most a handful of URLs. Nothing legitimate that
 * arrives here is larger than a few kilobytes, and without a limit the size of a request
 * is decided by whoever is sending it: a body arrives, the parser reads all of it into
 * memory, and the process is gone before anything has had a chance to validate a field.
 *
 * <p>Two checks, because there are two ways to send a body. A declared length is refused
 * before a byte is read. A chunked body has no declared length, so the stream itself is
 * wrapped and stops the moment it goes past — a caller that lies about its length is the
 * one this is for.
 */
public class RequestSize extends OncePerRequestFilter {

    private final int maxBytes;

    public RequestSize(int maxBytes) {
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");

        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        if (request.getContentLengthLong() > maxBytes) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }

        try {
            chain.doFilter(new AtMost(request, maxBytes), response);
        } catch (TooMuch tooMuch) {
            if (!response.isCommitted()) response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        }
    }

    /** A request whose body stops being readable past a point. */
    private static final class AtMost extends HttpServletRequestWrapper {

        private final int maxBytes;

        private AtMost(HttpServletRequest request, int maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream underneath = super.getInputStream();

            return new ServletInputStream() {

                private int read;

                @Override
                public int read() throws IOException {
                    int next = underneath.read();

                    if (next != -1 && ++read > maxBytes) throw new TooMuch();

                    return next;
                }

                @Override
                public boolean isFinished() {
                    return underneath.isFinished();
                }

                @Override
                public boolean isReady() {
                    return underneath.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    underneath.setReadListener(listener);
                }
            };
        }
    }

    /** Thrown from inside the parser, caught on the way back out. */
    private static final class TooMuch extends IOException {}
}
