/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.lucene.analysis.vi.v5;


import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

import ai.vitk.tok.DefaultDictionary;
import ai.vitk.tok.Dictionary;
import ai.vitk.type.Token;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.StringReader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Vietnamese Tokenizer.
 *
 * @author duydo
 */
public class VietnameseTokenizer extends Tokenizer {

    // private static final Dictionary dict = new DefaultDictionary();
    // private static final ai.vitk.tok.Tokenizer tokenizer = new ai.vitk.tok.Tokenizer(dict);

    private static Logger logger;
    static {
		if (logger == null) {
			logger = Logger.getLogger(VietnameseTokenizer.class.getName());
			// use a console handler to trace the log
//			logger.addHandler(new ConsoleHandler());
			try {
				logger.addHandler(new FileHandler("tokenizer_v5.log"));
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			logger.setLevel(Level.FINEST);
		}
	}

    private Iterator<Token> taggedWords;

    private int offset = 0;
    private int skippedPositions;

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
    private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);

    private ai.vitk.tok.Tokenizer tokenizer;

    public VietnameseTokenizer() {
        this(true, false);
    }

    public VietnameseTokenizer(boolean sentenceDetectorEnabled, boolean ambiguitiesResolved) {
        super();

        tokenizer = AccessController.doPrivileged(new PrivilegedAction<ai.vitk.tok.Tokenizer>() {
            @Override
            public ai.vitk.tok.Tokenizer run() {
                Dictionary dict = new DefaultDictionary();
                ai.vitk.tok.Tokenizer vnTokenizer = new ai.vitk.tok.Tokenizer(dict);
                return vnTokenizer;
            }
        });
    }

    private void tokenize(Reader input) throws IOException {
        final LineNumberReader reader = new LineNumberReader(input);
        String line = null;
        final List<Token> words = new ArrayList<Token>();
        while (true) {
            line = reader.readLine();
            if (line == null) {
                break;
            }
            List<List<Token>> result1 = tokenizer.iterate(line);
            for (List<Token> result2 : result1) {
                words.addAll(result2);
            }
        }
        taggedWords = words.iterator();
        
        logger.log(Level.INFO, words.toString());
    }

    @Override
    public final boolean incrementToken() throws IOException {
        clearAttributes();
        while (taggedWords.hasNext()) {
            final Token word = taggedWords.next();
            if (accept(word)) {
                posIncrAtt.setPositionIncrement(skippedPositions + 1);
                typeAtt.setType(word.getLemma());
                final int length = word.getWord().length();
                termAtt.copyBuffer(word.getWord().toCharArray(), 0, length);
                offsetAtt.setOffset(correctOffset(offset), offset = correctOffset(offset + length));
                offset++;
                return true;
            }
            skippedPositions++;
        }
        return false;
    }

    /**
     * Only accept the word characters.
     */
    private final boolean accept(Token word) {
        final String token = word.getWord();
        if (token.length() == 1) {
            return Character.isLetterOrDigit(token.charAt(0));
        }
        return true;
    }

    @Override
    public final void end() throws IOException {
        super.end();
        final int finalOffset = correctOffset(offset);
        offsetAtt.setOffset(finalOffset, finalOffset);
        posIncrAtt.setPositionIncrement(posIncrAtt.getPositionIncrement() + skippedPositions);
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        offset = 0;
        skippedPositions = 0;
        tokenize(input);
    }

    public static void main(String[] args) throws IOException {
        for (int tc = 0; tc < 10; tc++){
            final int name = tc;
            new Thread(new Runnable() {
                @Override
                public void run() {

                    try {
                        VietnameseTokenizer tokenizer = new VietnameseTokenizer();
                        tokenizer.tokenize(new StringReader("Ông Phan Mạnh Thắng, " +name+ " bộ trưởng bộ ngoại giao Việt Nam"));
                        while (tokenizer.taggedWords.hasNext()) {
                            final Token word = tokenizer.taggedWords.next();
                            System.out.println(word.getWord());
                        }
                        tokenizer.close();
                    } catch (IOException ex) {

                    }
                }
            }).start();
            
        }
    }
}
