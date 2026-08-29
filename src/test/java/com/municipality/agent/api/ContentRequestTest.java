package com.municipality.agent.api;

import com.municipality.agent.message.Audio;
import com.municipality.agent.message.ButtonReply;
import com.municipality.agent.message.Document;
import com.municipality.agent.message.Image;
import com.municipality.agent.message.Location;
import com.municipality.agent.message.Text;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wire format turning into what the agent works with.
 *
 * <p>Both sides are sealed, so this is the whole list on both. If a seventh kind of thing
 * a resident can send is ever added, this file stops compiling — which is the point of
 * having two sets of types instead of one.
 */
class ContentRequestTest {

    @Test
    void textIsWhatTheyTyped() {
        assertThat(new ContentRequest.TextContent("hola").asContent()).isEqualTo(new Text("hola"));
    }

    @Test
    void aVoiceNoteIsAUrlAndNothingElse() {
        assertThat(new ContentRequest.AudioContent("https://cdn/a.ogg").asContent())
                .isEqualTo(new Audio("https://cdn/a.ogg"));
    }

    @Test
    void aPhotoMayOrMayNotHaveACaption() {
        assertThat(new ContentRequest.ImageContent("https://cdn/1.jpg", "roto").asContent())
                .isEqualTo(new Image("https://cdn/1.jpg", "roto"));
        assertThat(new ContentRequest.ImageContent("https://cdn/1.jpg", null).asContent())
                .isEqualTo(new Image("https://cdn/1.jpg", null));
    }

    @Test
    void aFileMayOrMayNotHaveAName() {
        assertThat(new ContentRequest.DocumentContent("https://cdn/d.pdf", "dni.pdf").asContent())
                .isEqualTo(new Document("https://cdn/d.pdf", "dni.pdf"));
        assertThat(new ContentRequest.DocumentContent("https://cdn/d.pdf", null).asContent())
                .isEqualTo(new Document("https://cdn/d.pdf", null));
    }

    @Test
    void aPinIsTwoNumbers() {
        assertThat(new ContentRequest.LocationContent(-32.9468, -60.6393).asContent())
                .isEqualTo(new Location(-32.9468, -60.6393));
    }

    @Test
    void aTapCarriesBothTheIdAndWhatWasRead() {
        assertThat(new ContentRequest.ButtonContent("menu_reclamos", "Reclamos").asContent())
                .isEqualTo(new ButtonReply("menu_reclamos", "Reclamos"));
    }
}
