package com.mmx.order.adapter.in.rest.config;

import com.mmx.order.adapter.in.rest.generated.model.NoticePeriod;
import com.mmx.order.adapter.in.rest.generated.model.Tenor;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiEnumConverterConfiguration implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, Tenor.class, Tenor::fromValue);
        registry.addConverter(String.class, NoticePeriod.class, NoticePeriod::fromValue);
    }
}
