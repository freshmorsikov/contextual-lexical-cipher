# Contextual Lexical Cipher

## Idea

The project encodes source text into another natural, human-readable text while keeping it possible to decode the result back to the original text.

Instead of producing random-looking ciphertext, the encoder produces a sequence of ordinary words. 
The core feature is that an LLM helps make the encoded output read like natural text by choosing every single output word from a prepared list of allowed words.

The LLM does not choose arbitrary words. For each source symbol, the encoder provides only the words that are valid for that symbol, and the LLM selects one word from that list based on the words already generated. This preserves reversibility while allowing the output to sound more natural.

## Core concept

The system uses:

- Input text
- Secret key
- Dictionary of words [[Source](https://www.oxfordlearnersdictionaries.com/external/pdf/wordlists/oxford-3000-5000/The_Oxford_3000.pdf)]
- Alphabet of supported symbols 
- Mapping from alphabet symbols to word lists 
- LLM-powered word selector that chooses contextually suitable words from the allowed list

The key controls how the dictionary is shuffled. Because the same key always produces the same shuffled dictionary, both the encoder and decoder can rebuild the same symbol-to-word mapping independently.

## Encoding flow

1. Get the input `text` and secret `key`.
2. Shuffle the word dictionary using the `key`.
3. Split the shuffled dictionary into chunks.
4. Map each chunk to one symbol from the supported alphabet.
5. For every symbol in the input `text`:
   - Find the word list mapped to that symbol.
   - Ask the word selector to choose one word from that list.
   - Add the selected word to the encoded output.
6. Return the generated natural-language encoded text.

## Decoding flow

1. Get the encoded `text` and secret `key`.
2. Shuffle the same word dictionary using the `key`.
3. Split the shuffled dictionary into chunks.
4. Recreate the same mapping between alphabet symbols and word lists.
5. For every word in the encoded `text`:
   - Find which mapped word list contains that word.
   - Convert the word back to the symbol assigned to that list.
6. Return the original decoded text.

## Important property

The encoded text is reversible because every output word belongs to exactly one symbol bucket for a given key. 
The LLM can influence which valid word is selected, but it cannot change the mapping itself.

This means the project combines two goals:

- Cryptographic-style deterministic encoding based on a secret key.
- Natural-language generation where each encoded word is selected to fit the surrounding text.

## Cryptography aspect

This is a keyed substitution cipher at the word level. 
The secret key defines the shuffled dictionary and therefore the mapping between source symbols and output words.

The main security property is obscurity through the key-dependent mapping: without the same key, the decoder cannot reliably rebuild the word buckets.
However, this should be treated as an experimental lexical cipher.
Its strength depends on dictionary size, alphabet size, key quality, and resistance to frequency analysis.

## Example

Given an alphabet like:

```text
a b c ... A B C ... 0 1 2 ...
```

Example input:

```text
text: Meet at 9
key: open-sesame
```

Using the key, the dictionary is shuffled and split into buckets. The result is a key-dependent mapping from symbols to possible words:

```text
M -> [gentle, lantern, garden, ...]
e -> [morning, rain, window, ...]
t -> [falls, silver, path, ...]
  -> [near, lake, soft, ...]
a -> [quiet, around, river, ...]
9 -> [tonight, bright, corner, ...]
```

The encoder processes each symbol and lets the LLM choose one valid word from that symbol's bucket:

```text
M -> gentle
e -> morning
e -> rain
t -> falls
  -> near
a -> quiet
t -> silver
  -> lake
9 -> tonight
```

Encoded output:

```text
Gentle morning rain falls near quiet silver lake tonight.
```
