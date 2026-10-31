use librashader_preprocess::ShaderSource;
use librashader_presets::ShaderFeatures;
use librashader_reflect::front::SpirvCompilation;
fn main() {
    let paths: Vec<String> = std::env::args().skip(1).collect();
    assert!(!paths.is_empty());
    for path in paths {
        let source = ShaderSource::load(&path, ShaderFeatures::empty()).expect("Slang preprocessing failed");
        let _spirv = SpirvCompilation::try_from(&source).expect("SPIR-V compilation failed");
        println!("PASS: actual Slang vertex/fragment SPIR-V compilation: {path}");
    }
}
