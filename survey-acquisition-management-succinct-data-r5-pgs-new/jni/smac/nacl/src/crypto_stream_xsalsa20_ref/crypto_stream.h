#ifndef crypto_stream_H
#define crypto_stream_H

#include "crypto_stream_xsalsa20.h"

#define crypto_stream crypto_stream_xsalsa20
/* CHEESEBURGER crypto_stream_xsalsa20 */
#define crypto_stream_xor crypto_stream_xsalsa20_xor
/* CHEESEBURGER crypto_stream_xsalsa20_xor */
#define crypto_stream_beforenm crypto_stream_xsalsa20_beforenm
/* CHEESEBURGER crypto_stream_xsalsa20_beforenm */
#define crypto_stream_afternm crypto_stream_xsalsa20_afternm
/* CHEESEBURGER crypto_stream_xsalsa20_afternm */
#define crypto_stream_xor_afternm crypto_stream_xsalsa20_xor_afternm
/* CHEESEBURGER crypto_stream_xsalsa20_xor_afternm */
#define crypto_stream_KEYBYTES crypto_stream_xsalsa20_KEYBYTES
/* CHEESEBURGER crypto_stream_xsalsa20_KEYBYTES */
#define crypto_stream_NONCEBYTES crypto_stream_xsalsa20_NONCEBYTES
/* CHEESEBURGER crypto_stream_xsalsa20_NONCEBYTES */
#define crypto_stream_BEFORENMBYTES crypto_stream_xsalsa20_BEFORENMBYTES
/* CHEESEBURGER crypto_stream_xsalsa20_BEFORENMBYTES */
#define crypto_stream_PRIMITIVE "xsalsa20"
#define crypto_stream_IMPLEMENTATION crypto_stream_xsalsa20_IMPLEMENTATION
#define crypto_stream_VERSION crypto_stream_xsalsa20_VERSION

#endif
