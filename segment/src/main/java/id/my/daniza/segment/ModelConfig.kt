package id.my.daniza.segment

data class ModelConfig(
    val inputWidth: Int,
    val inputHeight: Int,
    val inputChannels: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val outputChannels: Int,
    val inputValueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val inputSize: Int get() = inputWidth * inputHeight * inputChannels
    val outputSize: Int get() = outputWidth * outputHeight * outputChannels

    companion object {
        val SINET = ModelConfig(
            inputWidth = 256, inputHeight = 256, inputChannels = 3,
            outputWidth = 256, outputHeight = 256, outputChannels = 2,
        )
        val MEDIAPIPE = ModelConfig(
            inputWidth = 256, inputHeight = 256, inputChannels = 3,
            outputWidth = 256, outputHeight = 256, outputChannels = 1,
        )
    }
}
