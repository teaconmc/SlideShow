package org.teacon.slides.texture;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.EOFException;
import java.io.IOException;
import java.util.Objects;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class LZWDecoder {

    private int mInitCodeSize, mClearCode, mEOFCode;
    private int mCodeSize, mCodeMask, mTableIndex, mPrevCode;

    // input data buffer
    private int mBlockPos;
    private int mBlockLength;
    private final byte[] mBlock = new byte[255];
    private int mInData;
    private int mInBits;

    // table
    private final int[] mPrefix = new int[4096];
    private final byte[] mSuffix = new byte[4096];
    private final byte[] mInitial = new byte[4096];
    private final int[] mLength = new int[4096];
    private final byte[] mString = new byte[4096];

    private @Nullable GIFDecoder mContext;

    public LZWDecoder() {
    }

    public byte[] setContext(@Nonnull GIFDecoder context) throws IOException {
        mContext = context;
        mBlockPos = 0;
        mBlockLength = 0;
        mInData = 0;
        mInBits = 0;
        mInitCodeSize = context.readByte();
        mClearCode = 1 << mInitCodeSize;
        mEOFCode = mClearCode + 1;
        initTable();
        return mString;
    }

    // decode next string of data, which can be accessed by setContext() method
    public final int readString() throws IOException {
        int code = getCode();
        if (code == mEOFCode) {
            return -1;
        } else if (code == mClearCode) {
            initTable();
            code = getCode();
            if (code == mEOFCode) {
                return -1;
            }
        } else {
            int newSuffixIndex;
            if (code < mTableIndex) {
                newSuffixIndex = code;
            } else { // code == mTableIndex
                newSuffixIndex = mPrevCode;
                if (code != mTableIndex) {
                    throw new IOException();
                }
            }

            if (mTableIndex < 4096) {
                int tableIndex = mTableIndex;
                int prevCode = mPrevCode;

                mPrefix[tableIndex] = prevCode;
                mSuffix[tableIndex] = mInitial[newSuffixIndex];
                mInitial[tableIndex] = mInitial[prevCode];
                mLength[tableIndex] = mLength[prevCode] + 1;

                ++mTableIndex;
                if ((mTableIndex == (1 << mCodeSize)) && (mTableIndex < 4096)) {
                    ++mCodeSize;
                    mCodeMask = (1 << mCodeSize) - 1;
                }
            }
        }
        // reverse
        int c = code;
        int len = mLength[c];
        for (int i = len - 1; i >= 0; i--) {
            mString[i] = mSuffix[c];
            c = mPrefix[c];
        }

        mPrevCode = code;
        return len;
    }

    private void initTable() {
        int size = 1 << mInitCodeSize;
        for (int i = 0; i < size; i++) {
            mPrefix[i] = -1;
            mSuffix[i] = (byte) i;
            mInitial[i] = (byte) i;
            mLength[i] = 1;
        }

        for (int i = size; i < 4096; i++) {
            mPrefix[i] = -1;
            mSuffix[i] = 0;
            mInitial[i] = 0;
            mLength[i] = 1;
        }

        mCodeSize = mInitCodeSize + 1;
        mCodeMask = (1 << mCodeSize) - 1;
        mTableIndex = size + 2;
        mPrevCode = 0;
    }

    private int getCode() throws IOException {
        while (mInBits < mCodeSize) {
            mInData |= readByte() << mInBits;
            mInBits += 8;
        }
        int code = mInData & mCodeMask;
        mInBits -= mCodeSize;
        mInData >>>= mCodeSize;
        return code;
    }

    private int readByte() throws IOException {
        if (mBlockPos == mBlockLength) {
            readBlock();
        }
        return (int) mBlock[mBlockPos++] & 0xFF;
    }

    private void readBlock() throws IOException {
        mBlockPos = 0;
        if ((mBlockLength = Objects.requireNonNull(mContext).readByte()) > 0) {
            mContext.readBytes(mBlock, 0, mBlockLength);
        } else {
            throw new EOFException();
        }
    }
}
