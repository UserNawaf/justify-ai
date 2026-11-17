package com.justify.core.pipeline;

import com.justify.core.AiUnavailableException;
import com.justify.core.Extraction;
import com.justify.core.Template;

/** Port to the language model. The model only extracts fields and rates clarity (D2). */
public interface AiClient {

    /**
     * @throws AiUnavailableException if no usable reply could be obtained
     */
    Extraction extract(Template template, String text);
}
