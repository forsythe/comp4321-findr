package com.findr.service.parser;

import com.findr.object.Webpage;

import java.util.Optional;

/**
 * This interface describes what an ideal parser should do: given a raw url, turn it into a page.
 * We'll implement it separately, allowing us to easily swap out/modify this class in the future
 */
public interface Parser {
    Optional<Webpage> parse(String url, boolean handleRedirects);
}
